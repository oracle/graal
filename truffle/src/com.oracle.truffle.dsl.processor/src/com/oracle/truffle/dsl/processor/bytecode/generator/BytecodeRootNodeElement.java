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
import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.createNeverPartOfCompilation;
import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.mergeSuppressWarnings;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.firstLetterUpperCase;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.SEALED;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.element.Modifier.VOLATILE;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOError;
import java.io.IOException;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.util.AbstractList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
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
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.SuppressFBWarnings;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.bytecode.generator.BytecodeRootNodeElement.BuilderElement.OperationDataClassesFactory.ScopeDataElement;
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
import com.oracle.truffle.dsl.processor.java.compiler.CompilerFactory;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationValue;
import com.oracle.truffle.dsl.processor.java.model.CodeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeNames;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.ArrayCodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.WildcardTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeParameterElement;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.java.model.GeneratedTypeMirror;
import com.oracle.truffle.dsl.processor.model.SpecializationData;

/**
 * Central code generation class for Bytecode DSL root nodes.
 */
final class BytecodeRootNodeElement extends CodeTypeElement {

    private static final String USER_LOCALS_START_INDEX = "USER_LOCALS_START_INDEX";
    private static final String BCI_INDEX = "BCI_INDEX";
    private static final String COROUTINE_FRAME_INDEX = "COROUTINE_FRAME_INDEX";
    private static final String EMPTY_INT_ARRAY = "EMPTY_INT_ARRAY";

    // Bytecode version encoding: [tags][instrumentations][source bit]
    private static final int MAX_TAGS = 32;
    private static final int TAG_OFFSET = 32;
    private static final int MAX_INSTRUMENTATIONS = 31;
    private static final int INSTRUMENTATION_OFFSET = 1;

    // !Important: Keep these in sync with InstructionBytecodeSizeTest!
    // Estimated number of Java bytecodes per instruction.
    private static final int ESTIMATED_CUSTOM_INSTRUCTION_SIZE = 34;
    private static final int ESTIMATED_EXTRACTED_INSTRUCTION_SIZE = 18;
    // Estimated number of bytecodes needed if they are just part of the switch table.
    private static final int GROUP_DISPATCH_SIZE = 20;
    // Estimated number of java bytecodes needed for a bytecode loop including exception handling
    private static final int ESTIMATED_BYTECODE_FOOTPRINT = 2000;
    // Limit from HotSpot to be classified as a huge method and therefore not be JIT compiled
    private static final int JAVA_JIT_BYTECODE_LIMIT = 8000;

    private final ProcessorContext context = ProcessorContext.getInstance();
    private final TruffleTypes types = context.getTypes();
    private final BytecodeDSLModel model;

    /**
     * We generate several CodeTypeElements to implement a Bytecode DSL interpreter. For each type,
     * a corresponding Factory class generates it and its members.
     * <p>
     * When generating code, some factories need to refer to other generated types. We declare those
     * types here as fields to make them accessible (i.e. the fields below are *not* a complete list
     * of the generated types).
     */

    // The builder class invoked by the language parser to generate the bytecode.
    private final BuilderElement builder = new BuilderElement();
    private final TypeMirror bytecodeBuilderType;
    private final TypeMirror parserType;

    // Singleton field for an empty array.
    private final CodeVariableElement emptyObjectArray;

    // Singleton fields for accessing arrays and the frame.
    private final CodeVariableElement fastAccess;
    private final CodeVariableElement byteArraySupport;
    private final CodeVariableElement frameExtensions;

    // Root node and ContinuationLocation classes to support yield.
    private final ContinuationRootNodeImplElement continuationRootNodeImpl;
    private final ContinuationLocationElement continuationLocation;

    // Implementations of public classes that Truffle interpreters interact with.
    private final BytecodeRootNodesImplElement bytecodeRootNodesImpl = new BytecodeRootNodesImplElement();

    // Helper classes that map instructions/operations/tags to constant integral values.
    private final InstructionConstantsElement instructionsElement = new InstructionConstantsElement();
    private final OperationConstantsElement operationsElement = new OperationConstantsElement();
    private final FrameTagConstantsElement frameTagsElement;

    // Helper class that tracks the number of guest-language loop iterations. The count must be
    // wrapped in an object, otherwise the loop unrolling logic of ExplodeLoop.MERGE_EXPLODE
    // will
    // create a new "state" for each count.
    private final CodeTypeElement loopCounter = new CodeTypeElement(Set.of(PRIVATE, STATIC), ElementKind.CLASS, null, "LoopCounter");

    private CodeTypeElement configEncoder;
    private AbstractBytecodeNodeElement abstractBytecodeNode;
    private TagNodeElement tagNode;
    private TagRootNodeElement tagRootNode;
    private InstructionImplElement instructionImpl;
    private SerializationRootNodeElement serializationRootNode;

    private Map<TypeMirror, CodeExecutableElement> expectMethods = new HashMap<>();

    BytecodeRootNodeElement(BytecodeDSLModel model) {
        super(Set.of(PUBLIC, FINAL), ElementKind.CLASS, ElementUtils.findPackageElement(model.getTemplateType()), model.getName());
        if (model.hasErrors()) {
            throw new IllegalArgumentException("Models with errors are not supported.");
        }
        this.model = model;
        this.bytecodeBuilderType = builder.asType();
        this.parserType = generic(types.BytecodeParser, bytecodeBuilderType);
        setSuperClass(model.getTemplateType().asType());
        addField(this, Set.of(PRIVATE, STATIC, FINAL), int[].class, EMPTY_INT_ARRAY, "new int[0]");

        this.emptyObjectArray = addField(this, Set.of(PRIVATE, STATIC, FINAL), Object[].class, "EMPTY_ARRAY", "new Object[0]");
        this.fastAccess = addField(this, Set.of(PRIVATE, STATIC, FINAL), types.BytecodeDSLAccess, "ACCESS");
        this.fastAccess.setInit(createFastAccessFieldInitializer(model.allowUnsafe));
        this.byteArraySupport = addField(this, Set.of(PRIVATE, STATIC, FINAL), types.ByteArraySupport, "BYTES");
        this.byteArraySupport.createInitBuilder().startCall("ACCESS.getByteArraySupport").end();
        this.frameExtensions = addField(this, Set.of(PRIVATE, STATIC, FINAL), types.FrameExtensions, "FRAMES");
        this.frameExtensions.createInitBuilder().startCall("ACCESS.getFrameExtensions").end();

        // Print a summary of the model in a docstring at the start.
        this.createDocBuilder().startDoc().lines(model.pp()).end();
        mergeSuppressWarnings(this, "static-method");

        if (model.enableYield) {
            continuationRootNodeImpl = new ContinuationRootNodeImplElement();
            continuationLocation = new ContinuationLocationElement();
        } else {
            continuationRootNodeImpl = null;
            continuationLocation = null;
        }

        // Define constants for accessing the frame.
        this.addAll(createFrameLayoutConstants());

        if (model.usesBoxingElimination()) {
            frameTagsElement = new FrameTagConstantsElement();
        } else {
            frameTagsElement = null;
        }

        this.instructionImpl = this.add(new InstructionImplElement());

        if (model.enableTagInstrumentation) {
            this.tagNode = this.add(new TagNodeElement());
            this.tagRootNode = this.add(new TagRootNodeElement());
        }

        this.abstractBytecodeNode = this.add(new AbstractBytecodeNodeElement());
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

        CodeTreeBuilder frameType = this.add(
                        new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), generic(Class.class, new WildcardTypeMirror(types.VirtualFrame, null)), "FRAME_TYPE")).createInitBuilder();
        frameType.startStaticCall(types.Truffle, "getRuntime").end().startCall(".createVirtualFrame");
        frameType.string(emptyObjectArray.getSimpleName().toString());
        frameType.startGroup().startStaticCall(types.FrameDescriptor, "newBuilder").end().startCall(".build").end().end();
        frameType.end(); // call
        frameType.string(".getClass()");

        // Define the interpreter implementations.
        BytecodeNodeElement cachedBytecodeNode = this.add(new BytecodeNodeElement(InterpreterTier.CACHED));
        abstractBytecodeNode.getPermittedSubclasses().add(cachedBytecodeNode.asType());

        CodeTypeElement initialBytecodeNode;
        if (model.enableUncachedInterpreter) {
            CodeTypeElement uncachedBytecodeNode = this.add(new BytecodeNodeElement(InterpreterTier.UNCACHED));
            abstractBytecodeNode.getPermittedSubclasses().add(uncachedBytecodeNode.asType());
            initialBytecodeNode = uncachedBytecodeNode;
        } else {
            CodeTypeElement uninitializedBytecodeNode = this.add(new BytecodeNodeElement(InterpreterTier.UNINITIALIZED));
            abstractBytecodeNode.getPermittedSubclasses().add(uninitializedBytecodeNode.asType());
            initialBytecodeNode = uninitializedBytecodeNode;
        }

        // Define the builder class.
        builder.lazyInit();
        this.add(builder);

        instructionImpl.lazyInit();

        configEncoder = this.add(createBytecodeConfigEncoderClass());

        CodeExecutableElement newConfigBuilder = this.add(new CodeExecutableElement(Set.of(PUBLIC, STATIC), types.BytecodeConfig_Builder, "newConfigBuilder"));
        newConfigBuilder.createBuilder().startReturn().startStaticCall(types.BytecodeConfig, "newBuilder").staticReference(configEncoder.asType(), "INSTANCE").end().end();

        // Define implementations for the public classes that Truffle interpreters interact
        // with.
        bytecodeRootNodesImpl.lazyInit();
        this.add(bytecodeRootNodesImpl);

        // Define helper classes containing the constants for instructions and operations.
        instructionsElement.lazyInit();
        this.add(instructionsElement);

        operationsElement.lazyInit();
        this.add(operationsElement);

        if (model.usesBoxingElimination()) {
            this.add(frameTagsElement);
        }

        this.add(new ExceptionHandlerImplElement());
        this.add(new ExceptionHandlerListElement());
        this.add(new SourceInformationImplElement());
        this.add(new SourceInformationListElement());
        this.add(new SourceInformationTreeImplElement());
        this.add(new LocalVariableImplElement());
        this.add(new LocalVariableListElement());

        // Define the classes that implement continuations (yield).
        if (model.enableYield) {
            continuationRootNodeImpl.lazyInit();
            this.add(continuationRootNodeImpl);
            this.add(continuationLocation);
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
        this.add(createLoopCounter());

        // Define the static method to create a root node.
        this.add(createCreate());

        // Define serialization methods and helper fields.
        if (model.enableSerialization) {
            this.add(createSerialize());
            this.add(createDoSerialize());
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

        // Define helpers for variadic accesses.
        this.add(createReadVariadic());
        this.add(createMergeVariadic());

        // Define helpers for locals.
        this.add(createGetBytecodeNode());
        this.add(createGetBytecodeNodeImpl());
        this.add(createGetBytecodeRootNode());

        this.add(createGetRootNodes());
        this.addOptional(createCountTowardsStackTraceLimit());
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
        }

        if (model.isBytecodeUpdatable()) {
            // we add this last so we do not pick up this field for constructors
            abstractBytecodeNode.add(new CodeVariableElement(Set.of(VOLATILE), arrayOf(type(byte.class)), "oldBytecodes"));
        }

        // this should be at the end after all methods have been added.
        if (model.enableSerialization) {
            addMethodStubsToSerializationRootNode();
        }
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
        b.string("frame");
        if (model.enableYield) {
            b.string("frame");
            b.string("null");
        }

        b.end(2);

        return ex;
    }

    private CodeTypeElement createCachedDataClass(InstructionModel instr, StaticConstants consts) {
        NodeConstants nodeConsts = new NodeConstants();
        BytecodeDSLNodeGeneratorPlugs plugs = new BytecodeDSLNodeGeneratorPlugs(BytecodeRootNodeElement.this, instr);
        FlatNodeGenFactory factory = new FlatNodeGenFactory(context, GeneratorMode.DEFAULT, instr.nodeData, consts, nodeConsts, plugs);

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
    private String encodeState(String bci, String sp, String useContinuationFrame) {
        String result = "";
        if (useContinuationFrame != null) {
            if (!model.enableYield) {
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

    private String encodeState(String bci, String sp) {
        return encodeState(bci, sp, null);
    }

    private static final String RETURN_BCI = "0xFFFFFFFF";

    private static String encodeReturnState(String sp) {
        return String.format("((%s & 0xFFFFL) << 32) | %sL", sp, RETURN_BCI);
    }

    private static String encodeNewBci(String bci, String state) {
        return String.format("(%s & 0xFFFF00000000L) | (%s & 0xFFFFFFFFL)", state, bci);
    }

    private static String decodeBci(String state) {
        return String.format("(int) %s", state);
    }

    private static String decodeSp(String state) {
        return String.format("(short) (%s >>> 32)", state);
    }

    private String decodeUseContinuationFrame(String state) {
        if (!model.enableYield) {
            throw new AssertionError();
        }
        return String.format("(%s & (1L << 48)) != 0", state);
    }

    private String clearUseContinuationFrame(String target) {
        if (!model.enableYield) {
            throw new AssertionError();
        }
        return String.format("(%s & ~(1L << 48))", target);
    }

    private CodeExecutableElement createContinueAt() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(Object.class), "continueAt");
        ex.addParameter(new CodeVariableElement(abstractBytecodeNode.asType(), "bc"));
        ex.addParameter(new CodeVariableElement(type(int.class), "bci"));
        ex.addParameter(new CodeVariableElement(type(int.class), "sp"));
        ex.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));
        if (model.enableYield) {
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
            ex.addParameter(new CodeVariableElement(types.VirtualFrame, "localFrame"));
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
        if (model.enableYield) {
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

        if (model.isBytecodeUpdatable()) {
            b.declaration(abstractBytecodeNode.asType(), "oldBytecode", "bc");
            b.statement("bc = this.bytecode");
            b.startAssign("state").startCall("oldBytecode.transitionState");
            b.string("bc");
            b.string("state");
            if (model.enableYield) {
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
        if (model.storeBciInFrame) {
            b.statement("return true");
        } else {
            b.statement("return !compiled");
        }
        return ex;
    }

    private Element createFindBytecodeIndex() {
        CodeExecutableElement ex = overrideImplementRootNodeMethod(model, "findBytecodeIndex", new String[]{"node", "frame"});
        mergeSuppressWarnings(ex, "hiding");
        CodeTreeBuilder b = ex.createBuilder();
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
            b.startIf().string("current ").instanceOf(abstractBytecodeNode.asType()).string(" b").end().startBlock();
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
        b.startReturn().string("super.findInstrumentableCallNode(callNode, frame, bytecodeIndex)").end();
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

        if (model.enableYield) {
            result.add(createInitializedVariable(Set.of(PRIVATE, STATIC, FINAL), int.class, COROUTINE_FRAME_INDEX, reserved++ + ""));
        }

        result.add(createInitializedVariable(Set.of(PRIVATE, STATIC, FINAL), int.class, USER_LOCALS_START_INDEX, reserved + ""));

        return result;
    }

    private CodeTree createFastAccessFieldInitializer(boolean allowUnsafe) {
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

    private CodeExecutableElement createCountTowardsStackTraceLimit() {
        ExecutableElement executable = ElementUtils.findOverride(ElementUtils.findMethod(types.RootNode, "countsTowardsStackTraceLimit"), model.templateType);
        if (executable != null) {
            return null;
        }
        CodeExecutableElement ex = overrideImplementRootNodeMethod(model, "countsTowardsStackTraceLimit");
        if (ex.getModifiers().contains(Modifier.FINAL)) {
            // already overridden by the root node.
            return null;
        }

        ex.getModifiers().remove(Modifier.ABSTRACT);
        ex.getModifiers().add(Modifier.FINAL);
        CodeTreeBuilder b = ex.createBuilder();
        /*
         * We do override with false by default to avoid materialization of sources during stack
         * walking.
         */
        b.returnTrue();
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

    private CodeExecutableElement withTruffleBoundary(CodeExecutableElement ex) {
        ex.addAnnotationMirror(new CodeAnnotationMirror(types.CompilerDirectives_TruffleBoundary));
        return ex;
    }

    private CodeTypeElement createLoopCounter() {
        addField(loopCounter, Set.of(PRIVATE, STATIC, FINAL), int.class, "REPORT_LOOP_STRIDE", "1 << 8");
        addField(loopCounter, Set.of(PRIVATE), int.class, "value");

        return loopCounter;
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

        b.declaration("BytecodeRootNodesImpl", "nodes", "new BytecodeRootNodesImpl(parser, config)");
        b.startAssign("Builder builder").startNew(builder.getSimpleName().toString());
        b.string("language");
        b.string("nodes");
        b.string("config");
        b.end(2);

        b.startStatement().startCall("parser", "parse");
        b.string("builder");
        b.end(2);

        b.startStatement().startCall("builder", "finish").end(2);

        b.startReturn().string("nodes").end();

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
                        Unlike {@link BytecodeRootNodes#serialize}, this method does not use already-constructed root nodes,
                        so it cannot serialize field values that get set outside of the parser.

                        @param buffer the buffer to write the byte output to.
                        @param callback the language-specific serializer for constants in the bytecode.
                        @param parser the parser.
                        """);

        CodeTreeBuilder init = CodeTreeBuilder.createBuilder();
        init.startNew("Builder");
        init.string("null"); // language not needed for serialization
        init.startGroup();
        init.startNew(bytecodeRootNodesImpl.asType());
        init.string("parser");
        init.staticReference(types.BytecodeConfig, "COMPLETE");
        init.end(2);
        init.staticReference(types.BytecodeConfig, "COMPLETE");
        init.end();

        CodeTreeBuilder b = method.createBuilder();

        b.declaration("Builder", "builder", init.build());

        b.startStatement();
        b.startCall("doSerialize");
        b.string("buffer");
        b.string("callback");
        b.string("builder");
        b.string("null"); // existingNodes
        b.end(2);

        return withTruffleBoundary(method);
    }

    private CodeExecutableElement createDoSerialize() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(void.class), "doSerialize");
        method.addParameter(new CodeVariableElement(type(DataOutput.class), "buffer"));
        method.addParameter(new CodeVariableElement(types.BytecodeSerializer, "callback"));
        method.addParameter(new CodeVariableElement(bytecodeBuilderType, "builder"));
        method.addParameter(new CodeVariableElement(generic(List.class, model.getTemplateType().asType()), "existingNodes"));
        method.addThrownType(type(IOException.class));

        CodeTreeBuilder b = method.createBuilder();

        b.startTryBlock();

        b.startStatement().startCall("builder", "serialize");
        b.string("buffer");
        b.string("callback");
        b.string("existingNodes");
        b.end().end();

        b.end().startCatchBlock(type(IOError.class), "e");
        b.startThrow().cast(type(IOException.class), "e.getCause()").end();
        b.end();

        return withTruffleBoundary(method);
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

        b.startTryBlock();

        b.statement("return create(language, config, (b) -> b.deserialize(input, callback, null))");
        b.end().startCatchBlock(type(IOError.class), "e");
        b.startThrow().cast(type(IOException.class), "e.getCause()").end();
        b.end();

        return withTruffleBoundary(method);
    }

    private CodeExecutableElement createReadVariadic() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(Object[].class), "readVariadic");

        ex.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));
        ex.addParameter(new CodeVariableElement(type(int.class), "sp"));
        ex.addParameter(new CodeVariableElement(type(int.class), "variadicCount"));

        ex.addAnnotationMirror(createExplodeLoopAnnotation(null));

        CodeTreeBuilder b = ex.createBuilder();

        b.statement("Object[] result = new Object[variadicCount]");
        b.startFor().string("int i = 0; i < variadicCount; i++").end().startBlock();
        b.statement("int index = sp - variadicCount + i");
        b.statement("result[i] = " + uncheckedGetFrameObject("index"));
        b.statement(clearFrame("frame", "index"));
        b.end();

        b.statement("return result");

        return ex;
    }

    private CodeExecutableElement createMergeVariadic() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(Object[].class), "mergeVariadic");

        ex.addParameter(new CodeVariableElement(type(Object[].class), "array"));

        CodeTreeBuilder b = ex.createBuilder();

        b.statement("Object[] current = array");
        b.statement("int length = 0");
        b.startDoBlock();
        b.statement("int currentLength = current.length - 1");
        b.statement("length += currentLength");
        b.statement("current = (Object[]) current[currentLength]");
        b.end().startDoWhile().string("current != null").end();

        b.statement("Object[] newArray = new Object[length]");
        b.statement("current = array");
        b.statement("int index = 0");

        b.startDoBlock();
        b.statement("int currentLength = current.length - 1");
        b.statement("System.arraycopy(current, 0, newArray, index, currentLength)");
        b.statement("index += currentLength");
        b.statement("current = (Object[]) current[currentLength]");
        b.end().startDoWhile().string("current != null").end();

        b.startReturn().string("newArray").end();

        return ex;
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
        if (model.enableYield) {
            ex.addParameter(new CodeVariableElement(generic(ArrayList.class, continuationLocation.asType()), "continuationLocations"));
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

            if (model.enableYield) {
                // We need to patch the BytecodeNodes for continuations.
                b.startStatement().startCall("oldBytecode.updateContinuationRootNodes");
                b.string("newBytecode");
                b.string("reason");
                b.string("continuationLocations");
                b.string("bytecodes_ != null");
                b.end(2);
            }
        }

        b.startAssert().startStaticCall(type(Thread.class), "holdsLock").string("this.nodes").end().end();
        b.statement("var cloneReferences = this.clones");
        b.startIf().string("cloneReferences != null").end().startBlock();
        b.startStatement();
        b.string("cloneReferences.forEach((clone) -> ").startBlock();

        b.declaration(abstractBytecodeNode.asType(), "cloneOldBytecode");
        b.declaration(abstractBytecodeNode.asType(), "cloneNewBytecode");
        b.startDoBlock();
        b.statement("cloneOldBytecode = clone.bytecode");
        b.statement("cloneNewBytecode = clone.insert(this.bytecode.cloneUninitialized())");

        b.startIf().string("bytecodes_ == null").end().startBlock();
        b.lineComment("When bytecode doesn't change, nodes are reused and should be re-adopted.");
        b.statement("cloneNewBytecode.adoptNodesAfterUpdate()");
        b.end();
        emitFence(b);
        b.end().startDoWhile().startCall("!BYTECODE_UPDATER", "compareAndSet").string("clone").string("cloneOldBytecode").string("cloneNewBytecode").end().end();
        b.newLine();
        if (model.isBytecodeUpdatable()) {
            b.startIf().string("bytecodes_ != null").end().startBlock();
            b.startStatement().startCall("cloneOldBytecode.invalidate");
            b.string("cloneNewBytecode");
            b.string("reason");
            b.end(2);
            b.end();
            if (model.enableYield) {
                // We need to patch the BytecodeNodes for continuations.
                b.startStatement().startCall("cloneOldBytecode.updateContinuationRootNodes");
                b.string("cloneNewBytecode");
                b.string("reason");
                b.string("continuationLocations");
                b.string("bytecodes_ != null");
                b.end(2);
            }
        }

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
        if (model.getInstrumentations().size() > MAX_INSTRUMENTATIONS) {
            throw new AssertionError("Unsupported instrumentation size.");
        }
        if (model.getProvidedTags().size() > MAX_TAGS) {
            throw new AssertionError("Unsupported instrumentation size.");
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

        if (!model.getInstrumentations().isEmpty()) {
            b.declaration("long", "encoding", "0L");
            boolean elseIf = false;
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
        if (!model.getInstrumentations().isEmpty()) {
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
        CodeTreeBuilder b = ex.createBuilder();
        // Disable compilation for the uncached interpreter.
        b.startReturn().string("bytecode.getTier() != ").staticReference(types.BytecodeTier, "UNCACHED").end();
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
        return factory.createExecuteMethod(el, executable, specializations, skipStateChecks && instruction.isQuickening());
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

    private TypeMirror type(Class<?> c) {
        return context.getType(c);
    }

    private DeclaredType declaredType(Class<?> t) {
        return context.getDeclaredType(t);
    }

    private static String executeMethodName(InstructionModel instruction) {
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

    private static CodeExecutableElement overrideImplementRootNodeMethod(BytecodeDSLModel model, String name, String[] parameterNames) {
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

    private static boolean needsCachedInitialization(InstructionModel instruction, InstructionImmediate immediate) {
        return switch (immediate.kind()) {
            case NODE_PROFILE -> true;
            // branch.backward does not need its own profile (it references an existing profile).
            case BRANCH_PROFILE -> instruction.kind != InstructionKind.BRANCH_BACKWARD;
            default -> false;
        };
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

    private static boolean hasUnexpectedExecuteValue(InstructionModel instr) {
        return ElementUtils.needsCastTo(instr.getQuickeningRoot().signature.returnType, instr.signature.returnType);
    }

    private static Collection<List<InstructionModel>> groupInstructionsByLength(Collection<InstructionModel> models) {
        return models.stream().sorted(Comparator.comparingInt((i) -> i.getInstructionLength())).collect(deterministicGroupingBy((m) -> m.getInstructionLength())).values();
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

    private CodeVariableElement compFinal(CodeVariableElement fld) {
        return compFinal(-1, fld);
    }

    private CodeVariableElement compFinal(int dims, CodeVariableElement fld) {
        CodeAnnotationMirror mir = new CodeAnnotationMirror(types.CompilerDirectives_CompilationFinal);
        if (dims != -1) {
            mir.setElementValue("dimensions", new CodeAnnotationValue(dims));
        }
        fld.addAnnotationMirror(mir);
        return fld;
    }

    private CodeVariableElement child(CodeVariableElement fld) {
        CodeAnnotationMirror mir = new CodeAnnotationMirror(fld.asType().getKind() == TypeKind.ARRAY ? types.Node_Children : types.Node_Child);
        fld.addAnnotationMirror(mir);
        return fld;
    }

    private void emitFence(CodeTreeBuilder b) {
        b.startStatement().startStaticCall(type(VarHandle.class), "storeStoreFence").end(2);
    }

    private static void emitThrowAssertionError(CodeTreeBuilder b, String reason) {
        b.startThrow().startCall("assertionFailed").string(reason).end(2);
    }

    private void emitThrowEncodingException(CodeTreeBuilder b, String reason) {
        b.startThrow().startStaticCall(types.BytecodeEncodingException, "create");
        b.string(reason);
        b.end(2);
    }

    private static void emitThrowIllegalArgumentException(CodeTreeBuilder b, String reasonString) {
        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
        b.startThrow().startNew(ProcessorContext.getInstance().getType(IllegalArgumentException.class));
        if (reasonString != null) {
            b.doubleQuote(reasonString);
        }
        b.end(2);
    }

    private static void emitThrowIllegalStateException(CodeExecutableElement method, CodeTreeBuilder b, String reasonString) {
        GeneratorUtils.addBoundaryOrTransferToInterpreter(method, b);
        b.startThrow().startNew(ProcessorContext.getInstance().getType(IllegalStateException.class));
        if (reasonString != null) {
            b.doubleQuote(reasonString);
        }
        b.end(2);
    }

    private static void addJavadoc(CodeElement<?> element, String contents) {
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

    private CodeAnnotationMirror createExplodeLoopAnnotation(String kind) {
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

    private CodeTree createOperationConstant(OperationModel op) {
        return CodeTreeBuilder.createBuilder().staticReference(operationsElement.asType(), op.getConstantName()).build();
    }

    private static String safeCastShort(String value) {
        return String.format("safeCastShort(%s)", value);
    }

    private String localFrame() {
        return model.enableYield ? "localFrame" : "frame";
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

    private static CodeTree writeInstruction(String bc, String bci, String value) {
        return writeInstruction(bc, bci, CodeTreeBuilder.singleString(value));
    }

    private static CodeTree writeInstruction(String bc, String bci, CodeTree value) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.startCall("BYTES", "putShort");
        b.string(bc);
        b.string(bci);
        b.tree(value);
        b.end();
        return b.build();
    }

    static CodeTree readImmediate(String bc, String bci, InstructionImmediate immediate) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        String accessor = switch (immediate.kind().width) {
            case BYTE -> "getByte";
            case SHORT -> "getShort";
            case INT -> "getIntUnaligned";
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

    private static CodeTree writeImmediate(String bc, String bci, String value, InstructionImmediate immediate) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        String accessor = switch (immediate.kind().width) {
            case BYTE -> "putByte";
            case SHORT -> "putShort";
            case INT -> "putInt";
        };
        b.startCall("BYTES", accessor);
        b.string(bc);
        b.startGroup();
        b.string(bci).string(" + ").string(immediate.offset()).string(" ");
        b.startComment().string(" imm ", immediate.name(), " ").end();
        b.end();
        b.string(value);
        b.end();
        return b.build();
    }

    private static String readInt(String array, String index) {
        return String.format("BYTES.getInt(%s, %s)", array, index);
    }

    private static String writeInt(String array, String index, String value) {
        return String.format("BYTES.putInt(%s, %s, %s)", array, index, value);
    }

    private static String readByte(String array, String index) {
        return String.format("BYTES.getByte(%s, %s)", array, index);
    }

    private static String writeByte(String array, String index, String value) {
        return String.format("BYTES.putByte(%s, %s, %s)", array, index, value);
    }

    static CodeTree readConstFastPath(CodeTree index) {
        return readConst(index, "consts", null);
    }

    static CodeTree readConstFastPath(CodeTree index, TypeMirror knownType) {
        return readConst(index, "consts", knownType);
    }

    static CodeTree readConst(String index, String constants) {
        return readConst(CodeTreeBuilder.singleString(index), constants, null);
    }

    static CodeTree readConst(CodeTree index, String constants, TypeMirror knownType) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        boolean needsCast = knownType != null && !ElementUtils.isObject(knownType);
        if (needsCast) {
            b.startCall("ACCESS.uncheckedCast");
        }
        b.startCall("ACCESS.readObject");
        b.string(constants);
        b.tree(index);
        b.end();
        if (needsCast) {
            b.typeLiteral(ElementUtils.boxType(knownType));
            b.end();
        }
        return b.build();
    }

    private static CodeTree readIntArray(String array, String index) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.startCall("ACCESS.readInt");
        b.string(array);
        b.string(index);
        b.end();
        return b.build();
    }

    private static CodeTree writeIntArray(String array, String index, String value) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.startCall("ACCESS.writeInt");
        b.string(array);
        b.string(index);
        b.string(value);
        b.end();
        return b.build();
    }

    private static CodeTree readTagNode(TypeMirror expectedType, CodeTree index) {
        return readTagNode(expectedType, "tagRoot.tagNodes", index);
    }

    private static CodeTree readTagNode(TypeMirror expectedType, String tagNodes, CodeTree index) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.startCall("ACCESS.uncheckedCast");
        b.startCall("ACCESS.readObject");
        b.string(tagNodes);
        b.tree(index);
        b.end();

        b.typeLiteral(expectedType);
        b.end();
        return b.build();
    }

    private static CodeTree readTagNodeSafe(CodeTree index) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.string("tagRoot.tagNodes[" + index + "]");
        b.end();
        return b.build();
    }

    private static CodeTree readNodeProfile(TypeMirror expectedType, CodeTree index) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.startCall("ACCESS.uncheckedCast");
        b.startCall("ACCESS.readObject");
        b.string("cachedNodes");
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

    private static CodeTreeBuilder startRequireFrame(CodeTreeBuilder b, TypeMirror type) {
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

    private static String setFrameObject(String index, String value) {
        return setFrameObject("frame", index, value);
    }

    private static String setFrameObject(String frame, String index, String value) {
        return String.format("FRAMES.setObject(%s, %s, %s)", frame, index, value);
    }

    private static String clearFrame(String frame, String index) {
        return String.format("FRAMES.clear(%s, %s)", frame, index);
    }

    private static String copyFrameSlot(String src, String dst) {
        return String.format("FRAMES.copy(frame, %s, %s)", src, dst);
    }

    private static String copyFrameTo(String srcFrame, String srcOffset, String dstFrame, String dstOffset, String length) {
        return String.format("FRAMES.copyTo(%s, %s, %s, %s, %s)", srcFrame, srcOffset, dstFrame, dstOffset, length);
    }

    private static String cachedDataClassName(InstructionModel instr) {
        if (!instr.hasNodeImmediate() && !instr.canUseNodeSingleton()) {
            return null;
        }
        if (instr.quickeningBase != null) {
            return cachedDataClassName(instr.quickeningBase);
        }
        return instr.getInternalName() + "Node";
    }

    private static String childString(int numChildren) {
        return numChildren + ((numChildren == 1) ? " child" : " children");
    }

    enum InterpreterTier {
        UNINITIALIZED("Uninitialized"),
        UNCACHED("Uncached"),
        CACHED("Cached");

        final String friendlyName;

        InterpreterTier(String friendlyName) {
            this.friendlyName = friendlyName;
        }

        boolean isUncached() {
            return switch (this) {
                case UNINITIALIZED -> false;
                case UNCACHED -> true;
                case CACHED -> false;
            };
        }

        boolean isCached() {
            return switch (this) {
                case UNINITIALIZED -> false;
                case UNCACHED -> false;
                case CACHED -> true;
            };
        }

        boolean isUninitialized() {
            return switch (this) {
                case UNINITIALIZED -> true;
                case UNCACHED -> false;
                case CACHED -> false;
            };
        }

        public String bytecodeClassName() {
            return friendlyName + "BytecodeNode";
        }
    }

    final class BuilderElement extends CodeTypeElement {
        private static final String UNINIT = "UNINITIALIZED";

        private final CodeVariableElement uninitialized = add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(byte.class), UNINIT));
        private final SavedStateElement savedState = add(new SavedStateElement());
        private final OperationStackEntryElement operationStackEntry = add(new OperationStackEntryElement());
        private final ConstantPoolElement constantPool = add(new ConstantPoolElement());

        private final BytecodeLocalImplElement bytecodeLocalImpl = add(new BytecodeLocalImplElement());
        private final BytecodeLabelImplElement bytecodeLabelImpl = add(new BytecodeLabelImplElement());

        private final TypeMirror unresolvedLabelsType = generic(HashMap.class, types.BytecodeLabel, generic(context.getDeclaredType(ArrayList.class), context.getDeclaredType(Integer.class)));
        private final Map<InstructionEncoding, CodeExecutableElement> doEmitInstructionMethods = new TreeMap<>();
        private final Map<OperationModel, CodeTypeElement> dataClasses = new HashMap<>();

        private ScopeDataElement scopeDataType;

        private List<CodeVariableElement> builderState;

        private SerializationLocalElement serializationLocal;
        private SerializationLabelElement serializationLabel;
        private SerializationStateElement serializationElements;
        private DeserializationStateElement deserializationElement;
        private CodeVariableElement serialization;

        private CodeExecutableElement validateLocalScope;
        private CodeExecutableElement validateMaterializedLocalScope;

        BuilderElement() {
            super(Set.of(PUBLIC, STATIC, FINAL), ElementKind.CLASS, null, "Builder");
        }

        void lazyInit() {
            addJavadoc(this, """
                            Builder class to generate bytecode. An interpreter can invoke this class with its {@link com.oracle.truffle.api.bytecode.BytecodeParser} to generate bytecode.
                            """);
            this.setSuperClass(model.abstractBuilderType);
            this.setEnclosingElement(BytecodeRootNodeElement.this);

            this.builderState = createBuilderState();
            this.savedState.lazyInit(builderState);
            this.addAll(builderState);

            this.uninitialized.createInitBuilder().string(-1).end();
            this.add(createOperationNames());

            this.addAll(new OperationDataClassesFactory().create());
            this.operationStackEntry.lazyInit();
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
            this.add(new CodeVariableElement(Set.of(PRIVATE), type(int.class), "numRoots"));
            this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), generic(ArrayList.class, types.Source), "sources"));

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
            this.add(createRegisterUnresolvedLabel());
            this.add(createResolveUnresolvedLabel());

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
            this.add(createValidateRootOperationBegin());
            this.add(createGetCurrentRootOperationData());
            this.add(createBeforeChild());
            this.add(createAfterChild());
            this.add(createSafeCastShort());
            this.add(createCheckOverflowShort());
            this.add(createCheckOverflowInt());
            this.add(createCheckBci());
            this.add(createUpdateMaxStackHeight());
            this.add(createEnsureBytecodeCapacity());
            this.add(createDoEmitVariadic());
            this.add(createDoEmitFinallyHandler());
            this.add(createDoCreateExceptionHandler());
            this.add(createDoEmitSourceInfo());
            this.add(createFinish());
            this.add(createBeforeEmitBranch());
            this.add(createBeforeEmitReturn());
            this.add(createPatchHandlerTable());
            this.add(createDoEmitRoot());
            this.add(createAllocateNode());
            this.add(createAllocateBytecodeLocal());
            this.add(createAllocateBranchProfile());
            if (model.enableYield) {
                this.add(createAllocateContinuationConstant());

                if (model.enableTagInstrumentation) {
                    this.add(createDoEmitTagYield());
                    this.add(createDoEmitTagResume());
                }
            }

            if (model.enableBlockScoping) {
                this.add(createGetCurrentScope());
            }
            this.add(createDoEmitLocal());
            this.add(createDoEmitLocalConstantIndices());
            this.add(createAllocateLocalsTableEntry());

            if (model.enableSerialization) {
                this.add(createSerialize());
                this.add(createSerializeFinallyGenerator());
                this.add(createDeserialize());
            }

            this.add(createToString());
            this.add(createFailState());
            this.add(createFailArgument());
            this.add(createDumpAt());

            this.addAll(doEmitInstructionMethods.values());
        }

        private List<CodeVariableElement> createBuilderState() {
            List<CodeVariableElement> list = new ArrayList<>();
            list.addAll(List.of(
                            new CodeVariableElement(Set.of(PRIVATE), type(int.class), "operationSequenceNumber"),
                            new CodeVariableElement(Set.of(PRIVATE), new ArrayCodeTypeMirror(operationStackEntry.asType()), "operationStack"),
                            new CodeVariableElement(Set.of(PRIVATE), type(int.class), "operationSp"),
                            new CodeVariableElement(Set.of(PRIVATE), type(int.class), "rootOperationSp"),
                            new CodeVariableElement(Set.of(PRIVATE), type(int.class), "numLocals"),
                            new CodeVariableElement(Set.of(PRIVATE), type(int.class), "numLabels"),
                            new CodeVariableElement(Set.of(PRIVATE), type(int.class), "numNodes"),
                            new CodeVariableElement(Set.of(PRIVATE), type(int.class), "numHandlers"),
                            new CodeVariableElement(Set.of(PRIVATE), type(int.class), "numConditionalBranches"),
                            new CodeVariableElement(Set.of(PRIVATE), type(byte[].class), "bc"),
                            new CodeVariableElement(Set.of(PRIVATE), type(int.class), "bci"),
                            new CodeVariableElement(Set.of(PRIVATE), type(int.class), "currentStackHeight"),
                            new CodeVariableElement(Set.of(PRIVATE), type(int.class), "maxStackHeight"),
                            new CodeVariableElement(Set.of(PRIVATE), type(int[].class), "sourceInfo"),
                            new CodeVariableElement(Set.of(PRIVATE), type(int.class), "sourceInfoIndex"),
                            new CodeVariableElement(Set.of(PRIVATE), type(int[].class), "handlerTable"),
                            new CodeVariableElement(Set.of(PRIVATE), type(int.class), "handlerTableSize"),
                            new CodeVariableElement(Set.of(PRIVATE), arrayOf(type(int.class)), "locals"),
                            new CodeVariableElement(Set.of(PRIVATE), type(int.class), "localsTableIndex"),
                            new CodeVariableElement(Set.of(PRIVATE), unresolvedLabelsType, "unresolvedLabels"),
                            new CodeVariableElement(Set.of(PRIVATE), constantPool.asType(), "constantPool")));

            CodeVariableElement reachable = new CodeVariableElement(Set.of(PRIVATE), type(boolean.class), "reachable");
            reachable.createInitBuilder().string("true");
            list.add(reachable);

            if (model.enableYield) {
                /**
                 * Invariant: Continuation locations are sorted by bci, which means we can iterate
                 * over the bytecodes and continuation locations in lockstep (i.e., the i-th yield
                 * instruction uses the i-th continuation location).
                 */
                list.add(new CodeVariableElement(Set.of(PRIVATE), generic(ArrayList.class, continuationLocation.asType()), "continuationLocations"));
            }

            if (model.enableBlockScoping) {
                list.add(new CodeVariableElement(Set.of(PRIVATE), type(int.class), "maxLocals"));
            }

            if (model.enableTagInstrumentation) {
                list.add(new CodeVariableElement(Set.of(PRIVATE), generic(type(List.class), tagNode.asType()), "tagRoots"));
                list.add(new CodeVariableElement(Set.of(PRIVATE), generic(type(List.class), tagNode.asType()), "tagNodes"));
            }

            // must be last
            list.add(new CodeVariableElement(Set.of(PRIVATE), savedState.asType(), "savedState"));
            return list;
        }

        private String getDataClassName(OperationModel operation) {
            switch (operation.kind) {
                case STORE_LOCAL:
                case STORE_LOCAL_MATERIALIZED:
                    if (!model.usesBoxingElimination()) {
                        // optimization: we are reusing the bytecode local as data class
                        return ElementUtils.getSimpleName(bytecodeLocalImpl);
                    }
                    break;
                case LOAD_LOCAL_MATERIALIZED:
                case LOAD_LOCAL:
                    // optimization: we are reusing the bytecode local as data class
                    return ElementUtils.getSimpleName(bytecodeLocalImpl);
            }

            CodeTypeElement type = dataClasses.get(operation);
            if (type == null) {
                return null;
            }
            return type.getSimpleName().toString();
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
            b.statement("this.operationStack = new OperationStackEntry[8]");
            b.statement("this.rootOperationSp = -1");

            return ctor;
        }

        private CodeExecutableElement createParseConstructor() {
            CodeExecutableElement ctor = new CodeExecutableElement(Set.of(PRIVATE), null, "Builder");
            ctor.addParameter(new CodeVariableElement(model.languageClass, "language"));
            ctor.addParameter(new CodeVariableElement(bytecodeRootNodesImpl.asType(), "nodes"));
            ctor.addParameter(new CodeVariableElement(types.BytecodeConfig, "config"));

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
            b.statement("long encoding = BytecodeConfigEncoderImpl.decode(config)");
            b.statement("this.tags = (int)((encoding >> " + TAG_OFFSET + ") & 0xFFFF_FFFF)");
            b.statement("this.instrumentations = (int)((encoding >> " + INSTRUMENTATION_OFFSET + ") & 0x7FFF_FFFF)");
            b.statement("this.parseSources = (encoding & 0x1) != 0");
            b.statement("this.parseBytecodes = true");
            b.statement("this.sources = parseSources ? new ArrayList<>(4) : null");
            b.statement("this.builtNodes = new ArrayList<>()");
            b.statement("this.operationStack = new OperationStackEntry[8]");
            b.statement("this.rootOperationSp = -1");

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
            b.statement("this.reachable = newReachable");
            b.startTryBlock();

            buildOperationStackWalk(b, () -> {
                b.declaration(operationStackEntry.asType(), "operation", "operationStack[i]");
                b.startSwitch().string("operation.operation").end().startBlock();
                for (OperationModel op : model.getOperations()) {
                    switch (op.kind) {
                        case ROOT:
                            b.startCase().tree(createOperationConstant(op)).end().startBlock();
                            emitCastOperationData(b, op, "i");
                            b.statement("operationData.reachable = newReachable");
                            b.statement("return");
                            b.end();
                            break;
                        case IF_THEN:
                            b.startCase().tree(createOperationConstant(op)).end().startBlock();
                            emitCastOperationData(b, op, "i");
                            b.startIf().string("operation.childCount == 0").end().startBlock();
                            b.lineComment("Unreachable condition branch makes the if and parent block unreachable.");
                            b.statement("operationData.thenReachable = newReachable");
                            b.statement("continue");
                            b.end().startElseIf().string("operation.childCount == 1").end().startBlock();
                            b.statement("operationData.thenReachable = newReachable");
                            b.end().startElseBlock();
                            b.lineComment("Invalid child index, but we will fail in the end method.");
                            b.end();
                            b.statement("return");
                            b.end();
                            break;
                        case IF_THEN_ELSE:
                            b.startCase().tree(createOperationConstant(op)).end().startBlock();
                            emitCastOperationData(b, op, "i");
                            b.startIf().string("operation.childCount == 0").end().startBlock();
                            b.lineComment("Unreachable condition branch makes the if, then and parent block unreachable.");
                            b.statement("operationData.thenReachable = newReachable");
                            b.statement("operationData.elseReachable = newReachable");
                            b.statement("continue");
                            b.end().startElseIf().string("operation.childCount == 1").end().startBlock();
                            b.statement("operationData.thenReachable = newReachable");
                            b.end().startElseIf().string("operation.childCount == 2").end().startBlock();
                            b.statement("operationData.elseReachable = newReachable");
                            b.end().startElseBlock();
                            b.lineComment("Invalid child index, but we will fail in the end method.");
                            b.end();
                            b.statement("return");
                            b.end();
                            break;
                        case CONDITIONAL:
                            b.startCase().tree(createOperationConstant(op)).end().startBlock();
                            emitCastOperationData(b, op, "i");
                            b.startIf().string("operation.childCount == 0").end().startBlock();
                            b.lineComment("Unreachable condition branch makes the if, then and parent block unreachable.");
                            b.statement("operationData.thenReachable = newReachable");
                            b.statement("operationData.elseReachable = newReachable");
                            b.statement("continue");
                            b.end().startElseIf().string("operation.childCount == 1").end().startBlock();
                            b.statement("operationData.thenReachable = newReachable");
                            b.end().startElseIf().string("operation.childCount == 2").end().startBlock();
                            b.statement("operationData.elseReachable = newReachable");
                            b.end().startElseBlock();
                            b.lineComment("Invalid child index, but we will fail in the end method.");
                            b.end();
                            b.statement("return");
                            b.end();
                            break;
                        case TRY_CATCH:
                            b.startCase().tree(createOperationConstant(op)).end().startBlock();
                            emitCastOperationData(b, op, "i");
                            b.startIf().string("operation.childCount == 0").end().startBlock();
                            b.statement("operationData.tryReachable = newReachable");
                            b.end().startElseIf().string("operation.childCount == 1").end().startBlock();
                            b.statement("operationData.catchReachable = newReachable");
                            b.end().startElseBlock();
                            b.lineComment("Invalid child index, but we will fail in the end method.");
                            b.end();
                            b.statement("return");
                            b.end();
                            break;
                        case WHILE:
                            b.startCase().tree(createOperationConstant(op)).end().startBlock();
                            emitCastOperationData(b, op, "i");
                            b.startIf().string("operation.childCount == 0").end().startBlock();
                            b.statement("operationData.bodyReachable = newReachable");
                            b.statement("continue");
                            b.end().startElseIf().string("operation.childCount == 1").end().startBlock();
                            b.statement("operationData.bodyReachable = newReachable");
                            b.end().startElseBlock();
                            b.lineComment("Invalid child index, but we will fail in the end method.");
                            b.end();
                            b.statement("return");
                            b.end();
                            break;
                        case TRY_FINALLY:
                        case TRY_CATCH_OTHERWISE:
                            b.startCase().tree(createOperationConstant(op)).end().startBlock();
                            emitCastOperationData(b, op, "i");
                            b.startIf().string("operation.childCount == 0").end().startBlock();
                            b.statement("operationData.tryReachable = newReachable");
                            if (op.kind == OperationKind.TRY_CATCH_OTHERWISE) {
                                b.end().startElseIf().string("operation.childCount == 1").end().startBlock();
                                b.statement("operationData.catchReachable = newReachable");
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
            b.startAssert().string("updateReachable() == this.reachable : ").doubleQuote("Inconsistent reachability detected.").end();
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
            b.statement("boolean oldReachable = reachable");
            buildOperationStackWalk(b, () -> {
                b.declaration(operationStackEntry.asType(), "operation", "operationStack[i]");
                b.startSwitch().string("operation.operation").end().startBlock();
                for (OperationModel op : model.getOperations()) {
                    switch (op.kind) {
                        case ROOT:
                            b.startCase().tree(createOperationConstant(op)).end().startBlock();
                            emitCastOperationData(b, op, "i");
                            b.statement("this.reachable = operationData.reachable");
                            b.statement("return oldReachable");
                            b.end();
                            break;
                        case IF_THEN:
                            b.startCase().tree(createOperationConstant(op)).end().startBlock();
                            emitCastOperationData(b, op, "i");
                            b.startIf().string("operation.childCount == 0").end().startBlock();
                            b.statement("continue");
                            b.end().startElseIf().string("operation.childCount == 1").end().startBlock();
                            b.statement("this.reachable = operationData.thenReachable");
                            b.end().startElseBlock();
                            b.lineComment("Invalid child index, but we will fail in the end method.");
                            b.end();
                            b.statement("return oldReachable");
                            b.end();
                            break;
                        case IF_THEN_ELSE:
                            b.startCase().tree(createOperationConstant(op)).end().startBlock();
                            emitCastOperationData(b, op, "i");
                            b.startIf().string("operation.childCount == 0").end().startBlock();
                            b.lineComment("Unreachable condition branch makes the if, then and parent block unreachable.");
                            b.statement("continue");
                            b.end().startElseIf().string("operation.childCount == 1").end().startBlock();
                            b.statement("this.reachable = operationData.thenReachable");
                            b.end().startElseIf().string("operation.childCount == 2").end().startBlock();
                            b.statement("this.reachable = operationData.elseReachable");
                            b.end().startElseBlock();
                            b.lineComment("Invalid child index, but we will fail in the end method.");
                            b.end();
                            b.statement("return oldReachable");
                            b.end();
                            break;
                        case CONDITIONAL:
                            b.startCase().tree(createOperationConstant(op)).end().startBlock();
                            emitCastOperationData(b, op, "i");
                            b.startIf().string("operation.childCount == 0").end().startBlock();
                            b.lineComment("Unreachable condition branch makes the if, then and parent block unreachable.");
                            b.statement("continue");
                            b.end().startElseIf().string("operation.childCount == 1").end().startBlock();
                            b.statement("this.reachable = operationData.thenReachable");
                            b.end().startElseIf().string("operation.childCount == 2").end().startBlock();
                            b.statement("this.reachable = operationData.elseReachable");
                            b.end().startElseBlock();
                            b.lineComment("Invalid child index, but we will fail in the end method.");
                            b.end();
                            b.statement("return oldReachable");
                            b.end();
                            break;
                        case TRY_CATCH:
                            b.startCase().tree(createOperationConstant(op)).end().startBlock();
                            emitCastOperationData(b, op, "i");
                            b.startIf().string("operation.childCount == 0").end().startBlock();
                            b.statement("this.reachable = operationData.tryReachable");
                            b.end().startElseIf().string("operation.childCount == 1").end().startBlock();
                            b.statement("this.reachable = operationData.catchReachable");
                            b.end().startElseBlock();
                            b.lineComment("Invalid child index, but we will fail in the end method.");
                            b.end();
                            b.statement("return oldReachable");
                            b.end();
                            break;
                        case WHILE:
                            b.startCase().tree(createOperationConstant(op)).end().startBlock();
                            emitCastOperationData(b, op, "i");
                            b.startIf().string("operation.childCount == 0").end().startBlock();
                            b.statement("continue");
                            b.end().startElseIf().string("operation.childCount == 1").end().startBlock();
                            b.statement("this.reachable = operationData.bodyReachable");
                            b.end().startElseBlock();
                            b.lineComment("Invalid child index, but we will fail in the end method.");
                            b.end();
                            b.statement("return oldReachable");
                            b.end();
                            break;
                        case TRY_FINALLY:
                        case TRY_CATCH_OTHERWISE:
                            b.startCase().tree(createOperationConstant(op)).end().startBlock();
                            emitCastOperationData(b, op, "i");
                            b.startIf().string("operation.childCount == 0").end().startBlock();
                            b.statement("this.reachable = operationData.tryReachable");
                            if (op.kind == OperationKind.TRY_CATCH_OTHERWISE) {
                                b.end().startElseIf().string("operation.childCount == 2").end().startBlock();
                                b.statement("this.reachable = operationData.catchReachable");
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
                    b.startCall("begin" + operation.name);
                } else {
                    b.startCall("emit" + operation.name);
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
                        b.type(BytecodeRootNodeElement.this.asType()).string(" node = ").cast(BytecodeRootNodeElement.this.asType()).string("end" + operation.name + "()");
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
                        b.startStatement().startCall("end" + operation.name);
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
                case LANGUAGE:
                    before.statement("serialization.language = language");
                    break;
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
                case OBJECT: {
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

        private void buildDeserializeOperationArgument(CodeTreeBuilder b, OperationArgument argument) {
            TypeMirror argType = argument.builderType();
            String argumentName = argument.name();
            switch (argument.kind()) {
                case LANGUAGE:
                    break;
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
                case OBJECT:
                    b.startDeclaration(argType, argumentName);
                    if (!ElementUtils.isObject(argType)) {
                        b.cast(argType);
                    }
                    b.string("context.consts.get(buffer.readInt())");
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

        private CodeExecutableElement createFinish() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "finish");
            CodeTreeBuilder b = ex.createBuilder();

            b.startIf().string("operationSp != 0").end().startBlock();
            b.startThrow().startCall("failState").doubleQuote("Unexpected parser end - there are still operations on the stack. Did you forget to end them?").end().end();
            b.end();

            b.startIf().string("reparseReason == null").end().startBlock();
            b.startStatement().string("nodes.setNodes(builtNodes.toArray(new ").type(BytecodeRootNodeElement.this.asType()).string("[0]))").end();
            b.end();
            b.statement("assert nodes.validate()");
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
                TypeMirror scopeType = scopeDataType.asType();
                b.declaration(scopeType, "scope", "getCurrentScope()");
                b.declaration(type(short.class), "localIndex", "allocateBytecodeLocal() /* unique global index */");
                b.declaration(type(short.class), "frameIndex", safeCastShort("USER_LOCALS_START_INDEX + scope.frameOffset + scope.numLocals") + " /* location in frame */");
                b.declaration(type(int.class), "tableIndex", "doEmitLocal(localIndex, frameIndex, name, info) /* index in global table */");
                b.statement("scope.registerLocal(tableIndex)");
            } else {
                b.declaration(type(short.class), "localIndex", "allocateBytecodeLocal() /* unique global index */");
                b.declaration(type(short.class), "frameIndex", safeCastShort("USER_LOCALS_START_INDEX + localIndex") + " /* location in frame */");
                b.statement("doEmitLocal(name, info)");
            }

            b.startDeclaration(bytecodeLocalImpl.asType(), "local");

            b.startNew(bytecodeLocalImpl.asType()).string("frameIndex");
            b.string("localIndex");
            b.string("((RootData) operationStack[this.rootOperationSp].data).index");
            if (model.enableBlockScoping) {
                b.string("scope");
            }

            b.end(); // new

            b.end();
            b.startReturn().string("local").end();
            return ex;
        }

        private CodeExecutableElement createGetCurrentScope() {
            TypeMirror scopeType = scopeDataType.asType();
            CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE),
                            scopeType, "getCurrentScope");
            CodeTreeBuilder b = method.createBuilder();
            buildOperationStackWalk(b, () -> {
                b.startIf().string("operationStack[i].data instanceof ").type(scopeType).string(" e").end().startBlock();
                b.statement("return e");
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
            b.startIf();
            b.string("operationSp == 0 || (operationStack[operationSp - 1].operation != ").tree(createOperationConstant(model.blockOperation));
            b.string(" && operationStack[operationSp - 1].operation != ").tree(createOperationConstant(model.rootOperation)).string(")");
            b.end().startBlock();
            b.startThrow().startCall("failState").doubleQuote("Labels must be created inside either Block or Root operations.").end().end();
            b.end();

            b.startAssign("BytecodeLabel result").startNew(bytecodeLabelImpl.asType());
            b.string("numLabels++");
            b.string(UNINIT);
            b.string("operationStack[operationSp - 1].sequenceNumber");
            b.end(2);

            b.statement("operationStack[operationSp - 1].addDeclaredLabel(result)");

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

        private CodeExecutableElement createRegisterUnresolvedLabel() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "registerUnresolvedLabel");
            ex.addParameter(new CodeVariableElement(types.BytecodeLabel, "label"));
            ex.addParameter(new CodeVariableElement(type(int.class), "immediateBci"));

            CodeTreeBuilder b = ex.createBuilder();
            b.declaration(generic(context.getDeclaredType(ArrayList.class), context.getDeclaredType(Integer.class)), "locations", "unresolvedLabels.computeIfAbsent(label, k -> new ArrayList<>())");
            b.startStatement().startCall("locations.add");
            b.string("immediateBci");
            b.end(2);

            return ex;
        }

        private CodeExecutableElement createResolveUnresolvedLabel() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "resolveUnresolvedLabel");
            ex.addParameter(new CodeVariableElement(types.BytecodeLabel, "label"));
            ex.addParameter(new CodeVariableElement(type(int.class), "stackHeight"));

            CodeTreeBuilder b = ex.createBuilder();

            b.statement("BytecodeLabelImpl impl = (BytecodeLabelImpl) label");
            b.statement("assert !impl.isDefined()");
            b.statement("impl.bci = bci");
            b.declaration(generic(List.class, context.getDeclaredType(Integer.class)), "sites", "unresolvedLabels.remove(impl)");
            b.startIf().string("sites != null").end().startBlock();
            b.startFor().startGroup().type(context.getDeclaredType(Integer.class)).string(" site : sites").end(2).startBlock();

            b.statement(writeInt("bc", "site", "impl.bci"));
            b.end(2);

            return ex;
        }

        private CodeVariableElement createOperationNames() {
            CodeVariableElement fld = new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(String[].class), "OPERATION_NAMES");

            CodeTreeBuilder b = fld.createInitBuilder();
            b.startNewArray((ArrayType) type(String[].class), null);
            b.string("null");

            int i = 1;
            for (OperationModel op : model.getOperations()) {
                if (op.id != i) {
                    throw new AssertionError();
                }

                i++;
                b.doubleQuote(op.name);
            }

            b.end();

            return fld;
        }

        private CodeExecutableElement createBeginOperation() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "beginOperation");
            ex.addParameter(new CodeVariableElement(type(int.class), "id"));
            ex.addParameter(new CodeVariableElement(type(Object.class), "data"));

            CodeTreeBuilder b = ex.createBuilder();

            b.startIf().string("operationSp == operationStack.length").end().startBlock(); // {
            b.startAssign("operationStack").startStaticCall(type(Arrays.class), "copyOf");
            b.string("operationStack");
            b.string("operationStack.length * 2");
            b.end(2);
            b.end(); // }

            b.startAssign("operationStack[operationSp++]").startNew(operationStackEntry.asType());
            b.string("id");
            b.string("data");
            b.string("operationSequenceNumber++");
            b.end(2);

            return ex;
        }

        private static String getBuilderMethodJavadocHeader(String action, OperationModel operation) {
            StringBuilder sb = new StringBuilder(action);

            if (operation.isCustom()) {
                sb.append(" a custom ");
                switch (operation.kind) {
                    case CUSTOM:
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
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(visibility), type(void.class), "begin" + operation.name);

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

            if (operation.constantOperands != null && operation.constantOperands.hasConstantOperands()) {
                int index = 0;
                for (ConstantOperandModel operand : operation.constantOperands.before()) {
                    buildConstantOperandValidation(b, operand.type(), operation.getOperationBeginArgumentName(index++));
                }
            }

            List<String> constantOperandIndices = emitConstantBeginOperands(b, operation);

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

            if (operation.kind == OperationKind.TAG) {
                b.declaration(tagNode.asType(), "node", "new TagNode(encodedTags & this.tags, bci)");
                b.startIf().string("tagNodes == null").end().startBlock();
                b.statement("tagNodes = new ArrayList<>()");
                b.end();
                b.declaration(type(int.class), "nodeId", "tagNodes.size()");
                b.statement("tagNodes.add(node)");
            }

            switch (operation.kind) {
                case ROOT:
                case BLOCK:
                    if (model.enableBlockScoping) {
                        b.declaration(scopeDataType.asType(), "parentScope", "getCurrentScope()");
                    }
                    break;
                case STORE_LOCAL: /* LOAD_LOCAL handled by createEmit */
                case STORE_LOCAL_MATERIALIZED:
                case LOAD_LOCAL_MATERIALIZED:
                    emitValidateLocalScope(b, operation);
                    break;
            }

            if (operation.kind != OperationKind.FINALLY_HANDLER) {
                b.startStatement().startCall("beforeChild").end(2);
            }

            /**
             * NB: createOperationBeginData is side-effecting: it can emit declarations that are
             * referenced by the returned CodeTree. We have to call it before we start the
             * beginOperation call.
             */
            CodeTree operationData = createOperationBeginData(b, operation, constantOperandIndices);
            if (operationData != null) {
                String dataClassName = getDataClassName(operation);
                b.declaration(dataClassName, "operationData", operationData);
                b.startStatement().startCall("beginOperation");
                b.tree(createOperationConstant(operation));
                b.string("operationData");
                b.end(2);
            } else {
                b.startStatement().startCall("beginOperation");
                b.tree(createOperationConstant(operation));
                b.string("null");
                b.end(2);
            }

            switch (operation.kind) {
                case BLOCK:
                    if (model.enableBlockScoping) {
                        b.statement("operationData.frameOffset = parentScope.frameOffset + parentScope.numLocals");
                    }
                    break;
                case TAG:
                    buildEmitInstruction(b, model.tagEnterInstruction, "nodeId");
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
                b.startIf().string("!localImpl.scope.valid").end().startBlock();
                b.startThrow().startCall("failArgument").doubleQuote("Local variable scope of this local is no longer valid.").end().end();
                b.end();
            }

            if (materialized) {
                // Local must belong to the current root node or an outer root node.
                if (model.enableBlockScoping) {
                    // The scope check above suffices to check nesting.
                } else {
                    // Otherwise, ensure the local's root is on the stack.
                    buildOperationStackWalk(b, "0", () -> {
                        b.startSwitch().string("operationStack[i].operation").end().startBlock();
                        b.startCase().tree(createOperationConstant(model.rootOperation)).end();
                        b.startCaseBlock();
                        emitCastOperationData(b, model.rootOperation, "i", "rootOperationData");
                        b.startIf().string("rootOperationData.index == localImpl.rootIndex").end().startBlock();
                        b.lineComment("root node found");
                        b.statement("return");
                        b.end();
                        b.end(); // case root
                        b.end(); // switch
                    });
                    b.startThrow().startCall("failArgument").doubleQuote(
                                    "Local variables used in materialized accesses must belong to the current root node or an outer root node.").end().end();
                }
            } else {
                // Local must belong to the current root node.
                b.declaration(dataClasses.get(model.rootOperation).asType(), "rootOperationData", "getCurrentRootOperationData()");
                b.startIf().string("rootOperationData.index != localImpl.rootIndex").end().startBlock();

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

            b.startIf().string("bc != null").end().startBlock(); // {
            b.startAssign("savedState").startNew(savedState.asType());
            b.variables(builderState);
            b.end(2);
            b.end(); // }

            /*
             * We initialize the fields declared on builderState here when beginRoot is called.
             */
            b.statement("operationSequenceNumber = 0");
            b.statement("rootOperationSp = operationSp");

            b.statement("reachable = true");
            if (model.enableTagInstrumentation) {
                b.statement("tagRoots = null");
                b.statement("tagNodes = null");
            }

            b.statement("numLocals = 0");
            if (model.enableBlockScoping) {
                b.statement("maxLocals = numLocals");
            }
            b.statement("numLabels = 0");
            b.statement("numNodes = 0");
            b.statement("numHandlers = 0");
            b.statement("numConditionalBranches = 0");
            b.statement("constantPool = new ConstantPool()");

            b.statement("bc = new byte[32]");
            b.statement("bci = 0");
            b.statement("currentStackHeight = 0");
            b.statement("maxStackHeight = 0");
            b.statement("handlerTable = new int[2 * EXCEPTION_HANDLER_LENGTH]");
            b.statement("handlerTableSize = 0");
            b.statement("locals = null");
            b.statement("localsTableIndex = 0");
            b.statement("unresolvedLabels = new HashMap<>()");
            if (model.enableYield) {
                b.statement("continuationLocations = new ArrayList<>()");
            }
            b.startIf().string("parseSources").end().startBlock();
            b.statement("sourceInfo = new int[3 * SOURCE_INFO_LENGTH]");
            b.statement("sourceInfoIndex = 0");
            b.end();

            b.startStatement().string("RootData operationData = ");
            b.tree(createOperationData("RootData", safeCastShort("numRoots++")));
            b.end();
            b.startIf().string("reparseReason == null").end().startBlock();
            b.statement("builtNodes.add(null)");
            b.startIf().string("builtNodes.size() > Short.MAX_VALUE").end().startBlock();
            emitThrowEncodingException(b, "\"Root node count exceeded maximum value.\"");
            b.end();
            b.end();

            if (model.enableBlockScoping) {
                b.statement("operationData.frameOffset = numLocals");
            }

            b.startStatement().startCall("beginOperation");
            b.tree(createOperationConstant(rootOperation));
            b.string("operationData");
            b.end(2);

            if (model.prolog != null || model.epilogExceptional != null || model.epilogReturn != null) {
                if (model.enableRootTagging) {
                    buildBegin(b, model.tagOperation, lookupTagConstant(types.StandardTags_RootTag).getSimpleName().toString());
                }

                // If prolog defined, emit prolog before Root's child.
                if (model.prolog != null) {
                    if (model.prolog.operation.operationEndArguments.length != 0) {
                        // If the prolog has end constants, we'll need to patch them in endRoot.
                        b.statement("operationData.prologBci = bci");
                    }

                    List<String> constantOperandIndices = emitConstantOperands(b, model.prolog.operation);
                    buildEmitOperationInstruction(b, model.prolog.operation, constantOperandIndices);
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

        private CodeTree createOperationBeginData(CodeTreeBuilder b, OperationModel operation, List<String> constantOperandIndices) {
            String className = getDataClassName(operation);
            return switch (operation.kind) {
                case STORE_LOCAL, STORE_LOCAL_MATERIALIZED -> {
                    String local = "(BytecodeLocalImpl)" + operation.getOperationBeginArgumentName(0);
                    if (model.usesBoxingElimination()) {
                        yield createOperationData(className, local);
                    } else {
                        yield CodeTreeBuilder.singleString(local);
                    }
                }
                case LOAD_LOCAL_MATERIALIZED, LOAD_LOCAL -> CodeTreeBuilder.singleString("(BytecodeLocalImpl)" + operation.getOperationBeginArgumentName(0));
                case IF_THEN -> createOperationData(className, "this.reachable");
                case IF_THEN_ELSE -> createOperationData(className, "this.reachable", "this.reachable");
                case CONDITIONAL -> createOperationData(className, "this.reachable", "this.reachable");
                case WHILE -> createOperationData(className, "bci", "this.reachable");
                case TRY_CATCH -> createOperationData(className, "++numHandlers", safeCastShort("currentStackHeight"), "bci", "this.reachable", "this.reachable", "this.reachable");
                case TRY_FINALLY -> createOperationData(className, "++numHandlers", safeCastShort("currentStackHeight"), operation.getOperationBeginArgumentName(0), "bci", "this.reachable",
                                "this.reachable",
                                "false");
                case TRY_CATCH_OTHERWISE -> createOperationData(className, "++numHandlers", safeCastShort("currentStackHeight"), operation.getOperationBeginArgumentName(0), "bci", "this.reachable",
                                "this.reachable",
                                "this.reachable");
                case FINALLY_HANDLER -> createOperationData(className, "finallyOperationSp");
                case CUSTOM, CUSTOM_INSTRUMENTATION -> {
                    if (operation.isTransparent) {
                        yield createOperationData(className);
                    } else {
                        // [childBcis, constants, locals...]
                        String[] args = new String[2];

                        CodeTreeBuilder childBciArrayBuilder = CodeTreeBuilder.createBuilder();
                        int numChildBcis = operation.instruction.getImmediates(ImmediateKind.BYTECODE_INDEX).size();
                        if (numChildBcis == 0) {
                            args[0] = EMPTY_INT_ARRAY;
                        } else {
                            childBciArrayBuilder.startNewArray(arrayOf(type(int.class)), null);
                            for (int i = 0; i < numChildBcis; i++) {
                                childBciArrayBuilder.string(UNINIT);
                            }
                            childBciArrayBuilder.end();
                            args[0] = childBciArrayBuilder.toString();
                        }

                        CodeTreeBuilder constantsArrayBuilder = CodeTreeBuilder.createBuilder();
                        if (constantOperandIndices == null || constantOperandIndices.size() == 0) {
                            args[1] = EMPTY_INT_ARRAY;
                        } else {
                            constantsArrayBuilder.startNewArray(arrayOf(type(int.class)), null);
                            for (String constantIndex : constantOperandIndices) {
                                constantsArrayBuilder.string(constantIndex);
                            }
                            constantsArrayBuilder.end();
                            args[1] = constantsArrayBuilder.toString();
                        }

                        yield createOperationData(className, args);
                    }
                }
                case CUSTOM_SHORT_CIRCUIT -> createOperationData(className);
                case TAG -> createOperationData(className, "nodeId", "this.reachable", "this.currentStackHeight", "node");
                case RETURN -> createOperationData(className);
                case BLOCK -> createOperationData(className, "this.currentStackHeight");
                case SOURCE -> {
                    b.startIf().string(operation.getOperationBeginArgumentName(0) + ".hasBytes()").end().startBlock();
                    b.startThrow().startCall("failArgument").doubleQuote("Byte-based sources are not supported.").end(2);
                    b.end();

                    b.statement("int index = sources.indexOf(" + operation.getOperationBeginArgumentName(0) + ")");
                    b.startIf().string("index == -1").end().startBlock();
                    b.statement("index = sources.size()");
                    b.statement("sources.add(" + operation.getOperationBeginArgumentName(0) + ")");
                    b.end();
                    yield createOperationData(className, "index");
                }
                case SOURCE_SECTION -> {
                    b.declaration(type(int.class), "foundSourceIndex", "-1");
                    b.string("loop: ");
                    // NB: walk entire operation stack, not just until root operation.
                    buildOperationStackWalk(b, "0", () -> {
                        b.startSwitch().string("operationStack[i].operation").end().startBlock();

                        b.startCase().tree(createOperationConstant(model.sourceOperation)).end();
                        b.startCaseBlock();
                        emitCastOperationData(b, model.sourceOperation, "i", "sourceData");
                        b.statement("foundSourceIndex = sourceData.sourceIndex");
                        b.statement("break loop");
                        b.end(); // case epilog
                        b.end(); // switch
                    });

                    b.startIf().string("foundSourceIndex == -1").end().startBlock();
                    b.startThrow().startCall("failState").doubleQuote("No enclosing Source operation found - each SourceSection must be enclosed in a Source operation.").end().end();
                    b.end();

                    String index = operation.getOperationBeginArgumentName(0);
                    String length = operation.getOperationBeginArgumentName(1);

                    // Negative values are only permitted for the combination (-1, -1).
                    b.startAssert().string("(", index, " == -1 && ", length, " == -1) || (", index, " >= 0 && ", length, " >= 0)").end();

                    b.declaration(type(int.class), "startBci");
                    b.startIf().string("rootOperationSp == -1").end().startBlock();
                    b.lineComment("not in a root yet");
                    b.statement("startBci = 0");
                    b.end().startElseBlock();
                    b.statement("startBci = bci");
                    b.end();

                    yield createOperationData(className, "foundSourceIndex", "startBci", index, length);
                }
                default -> {
                    if (operation.isTransparent) {
                        yield createOperationData(className);
                    } else {
                        yield null;
                    }
                }

            };
        }

        /**
         * For type-safety, we use data classes to manage operation state during building. These
         * data classes are generated by {@link OperationDataClassesFactory}.
         */
        private CodeTree createOperationData(String dataClassName, String... args) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.startNew(dataClassName);
            for (String arg : args) {
                b.string(arg);
            }
            b.end();

            return b.build();
        }

        private CodeExecutableElement createEndOperation() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), operationStackEntry.asType(), "endOperation");
            ex.addParameter(new CodeVariableElement(type(int.class), "id"));

            CodeTreeBuilder b = ex.createBuilder();

            b.startIf().string("operationSp == 0").end().startBlock(); // {
            b.startThrow().startCall("failState");
            b.doubleQuote("Unexpected operation end - there are no operations on the stack. Did you forget a beginRoot()?");
            b.end(2);
            b.end(); // }

            b.statement("OperationStackEntry entry = operationStack[operationSp - 1]");

            b.startIf().string("entry.operation != id").end().startBlock(); // {
            b.startThrow().startCall("failState");
            b.startGroup().doubleQuote("Unexpected operation end, expected end").string(" + OPERATION_NAMES[entry.operation] + ").doubleQuote(", but got end").string(" +  OPERATION_NAMES[id]").end();
            b.end(2); // throw, call
            b.end(); // }

            b.startIf().string("entry.declaredLabels != null").end().startBlock();
            b.startFor().string("BytecodeLabel label : entry.declaredLabels").end().startBlock();
            b.statement("BytecodeLabelImpl impl = (BytecodeLabelImpl) label");
            b.startIf().string("!impl.isDefined()").end().startBlock();
            b.startThrow().startCall("failState");
            b.string("\"Operation \" + OPERATION_NAMES[id] + \" ended without emitting one or more declared labels.\"");
            b.end(2); // throw, call
            b.end(3);

            b.statement("operationStack[operationSp - 1] = null");
            b.statement("operationSp -= 1");

            b.statement("return entry");

            return ex;
        }

        private CodeExecutableElement createEnd(OperationModel operation) {
            if (operation.kind == OperationKind.ROOT) {
                // endRoot is handled specially.
                return createEndRoot(operation);
            }

            Modifier visibility = operation.isInternal ? PRIVATE : PUBLIC;
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(visibility), type(void.class), "end" + operation.name);

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

            if (operation.constantOperands != null && operation.constantOperands.hasConstantOperands()) {
                int index = 0;
                for (ConstantOperandModel operand : operation.constantOperands.after()) {
                    buildConstantOperandValidation(b, operand.type(), operation.getOperationEndArgumentName(index++));
                }
            }

            List<String> constantOperandIndices = emitConstantOperands(b, operation);

            if (operation.kind == OperationKind.CUSTOM_INSTRUMENTATION) {
                int mask = 1 << operation.instrumentationIndex;
                b.startIf().string("(instrumentations & ").string("0x", Integer.toHexString(mask)).string(") == 0").end().startBlock();
                b.returnStatement();
                b.end();
            }

            if (operation.kind == OperationKind.FINALLY_HANDLER) {
                b.startStatement().startCall("endOperation");
                b.tree(createOperationConstant(operation));
                b.end(2);
                // FinallyHandler doesn't need to validate its children or call afterChild.
                return ex;
            }

            b.startDeclaration(operationStackEntry.asType(), "operation").startCall("endOperation");
            b.tree(createOperationConstant(operation));
            b.end(2);

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

            if (operation.isTransparent) {
                emitCastCurrentOperationData(b, operation);
            }

            switch (operation.kind) {
                case CUSTOM_SHORT_CIRCUIT:
                    InstructionModel shortCircuitInstruction = operation.instruction;
                    emitCastCurrentOperationData(b, operation);
                    if (shortCircuitInstruction.shortCircuitModel.returnConvertedBoolean()) {
                        /*
                         * All operands except the last are automatically converted when testing the
                         * short circuit condition. For the last operand we need to insert a
                         * conversion.
                         */
                        buildEmitBooleanConverterInstruction(b, shortCircuitInstruction);
                    }
                    // Go through the work list and fill in the branch target for each branch.
                    b.startFor().string("int site : operationData.branchFixupBcis").end().startBlock();
                    b.statement(writeInt("bc", "site", "bci"));
                    b.end();
                    break;
                case SOURCE_SECTION:
                    b.startStatement().startCall("doEmitSourceInfo");
                    b.string("operationData.sourceIndex");
                    b.string("operationData.startBci");
                    b.string("bci");
                    b.string("operationData.start");
                    b.string("operationData.length");
                    b.end(2);
                    break;
                case SOURCE:
                    break;
                case IF_THEN_ELSE:
                    emitCastCurrentOperationData(b, operation);
                    b.statement("markReachable(operationData.thenReachable || operationData.elseReachable)");
                    break;
                case IF_THEN:
                case WHILE:
                    b.statement("updateReachable()");
                    break;
                case CONDITIONAL:
                    emitCastCurrentOperationData(b, operation);
                    b.statement("markReachable(operationData.thenReachable || operationData.elseReachable)");
                    if (model.usesBoxingElimination()) {
                        buildEmitInstruction(b, operation.instruction, emitMergeConditionalArguments(operation.instruction));
                    }
                    break;
                case TRY_CATCH:
                    emitCastCurrentOperationData(b, operation);
                    b.statement("markReachable(operationData.tryReachable || operationData.catchReachable)");
                    break;
                case TRY_FINALLY:
                    emitCastCurrentOperationData(b, operation);
                    emitFinallyHandlersAfterTry(b, operation, "operationSp");
                    emitFixFinallyBranchBci(b);
                    b.statement("markReachable(operationData.tryReachable)");
                    break;
                case TRY_CATCH_OTHERWISE:
                    emitCastCurrentOperationData(b, operation);
                    b.statement("markReachable(operationData.tryReachable || operationData.catchReachable)").end();
                    break;
                case YIELD:
                    if (model.enableTagInstrumentation) {
                        b.statement("doEmitTagYield()");
                    }
                    buildEmitOperationInstruction(b, operation, null);

                    if (model.enableTagInstrumentation) {
                        b.statement("doEmitTagResume()");
                    }
                    break;
                case RETURN:
                    emitCastCurrentOperationData(b, operation);
                    b.statement("beforeEmitReturn(operationData.childBci)");
                    buildEmitOperationInstruction(b, operation, null);
                    b.statement("markReachable(false)");
                    break;
                case TAG:
                    b.declaration(tagNode.asType(), "tagNode", "operationData.node");

                    b.startIf().string("(encodedTags & this.tags) != tagNode.tags").end().startBlock();
                    emitThrowIllegalArgumentException(b, "The tags provided to endTag do not match the tags provided to the corresponding beginTag call.");
                    b.end();

                    b.lineComment("If this tag operation is nested in another, add it to the outer tag tree. Otherwise, it becomes a tag root.");
                    b.declaration(type(boolean.class), "outerTagFound", "false");
                    buildOperationStackWalk(b, () -> {
                        b.startIf().string("operationStack[i].data instanceof TagOperationData t").end().startBlock();
                        b.startIf().string("t.children == null").end().startBlock();
                        b.statement("t.children = new ArrayList<>(3)");
                        b.end();
                        b.statement("t.children.add(tagNode)");
                        b.statement("outerTagFound = true");
                        b.statement("break");
                        b.end(); // if tag operation
                    });

                    // Otherwise, this tag is the root of a tag tree.
                    b.startIf().string("!outerTagFound").end().startBlock();
                    b.startIf().string("tagRoots == null").end().startBlock();
                    b.statement("tagRoots = new ArrayList<>(3)");
                    b.end();
                    b.statement("tagRoots.add(tagNode)");
                    b.end(); // if !outerTagFound

                    b.declaration(arrayOf(tagNode.asType()), "children");
                    b.declaration(generic(type(List.class), tagNode.asType()), "operationChildren", "operationData.children");

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
                    b.statement("tagNode.returnBci = bci");

                    b.startIf().string("operationData.producedValue").end().startBlock();
                    String[] args;
                    InstructionImmediate imm = operation.instruction.getImmediate(ImmediateKind.BYTECODE_INDEX);
                    if (imm == null) {
                        args = new String[]{"operationData.nodeId"};
                    } else {
                        args = new String[]{"operationData.nodeId", "operationData.childBci"};
                    }

                    b.startIf().string("operationData.operationReachable").end().startBlock();
                    /*
                     * Leaving the tag leave is always reachable, because probes may decide to
                     * return at any point and we need a point where we can continue.
                     */
                    b.statement("markReachable(true)");
                    buildEmitInstruction(b, model.tagLeaveValueInstruction, args);
                    b.statement("doCreateExceptionHandler(operationData.handlerStartBci, bci, HANDLER_TAG_EXCEPTIONAL, operationData.nodeId, operationData.startStackHeight)");
                    b.end().startElseBlock();
                    buildEmitInstruction(b, model.tagLeaveValueInstruction, args);
                    b.end();

                    b.startStatement().startCall("afterChild");
                    b.string("true");
                    b.string("bci - " + model.tagLeaveValueInstruction.getInstructionLength());
                    b.end(2);

                    b.end().startElseBlock();

                    b.startIf().string("operationData.operationReachable").end().startBlock();
                    /*
                     * Leaving the tag leave is always reachable, because probes may decide to
                     * return at any point and we need a point where we can continue.
                     */
                    b.statement("markReachable(true)");
                    buildEmitInstruction(b, model.tagLeaveVoidInstruction, "operationData.nodeId");
                    b.statement("doCreateExceptionHandler(operationData.handlerStartBci, bci, HANDLER_TAG_EXCEPTIONAL, operationData.nodeId, operationData.startStackHeight)");
                    b.end().startElseBlock();
                    buildEmitInstruction(b, model.tagLeaveVoidInstruction, "operationData.nodeId");
                    b.end();

                    b.startStatement().startCall("afterChild");
                    b.string("false");
                    b.string("-1");
                    b.end(2);

                    b.end();

                    break;
                case BLOCK:
                    if (model.enableBlockScoping) {
                        // local table entries are emitted at the end of the block.
                        createEndLocalsBlock(b, false);
                    }
                    break;
                default:
                    if (operation.instruction != null) {
                        buildEmitOperationInstruction(b, operation, constantOperandIndices);
                    }
                    break;
            }

            if (operation.kind == OperationKind.TAG) {
                // handled in tag section
            } else if (operation.isTransparent) {
                // custom transparent operations have the operation cast on the stack
                b.startStatement().startCall("afterChild");
                b.string("operationData.producedValue");
                b.string("operationData.childBci");
                b.end(2);
            } else if (operation.kind == OperationKind.CUSTOM_SHORT_CIRCUIT) {
                b.declaration(type(int.class), "nextBci");
                b.startIf().string("operation.childCount <= 1").end().startBlock();
                b.lineComment("Single child -> boxing elimination possible");
                b.startStatement().string("nextBci = ");
                ShortCircuitInstructionModel shortCircuitModel = operation.instruction.shortCircuitModel;
                if (shortCircuitModel.returnConvertedBoolean()) {
                    // We emit a boolean converter instruction above. Compute its bci.
                    b.string("bci - " + shortCircuitModel.booleanConverterInstruction().getInstructionLength());
                } else {
                    // The child bci points to the instruction producing this last value.
                    b.string("operationData.childBci");
                }
                b.end();  // statement
                b.end(); // if block

                b.startElseBlock();
                b.lineComment("Multi child -> boxing elimination not possible use short-circuit bci to disable it.");
                b.statement("nextBci = operationData.shortCircuitBci");
                b.end();

                b.startStatement().startCall("afterChild");
                b.string("true").string("nextBci");
                b.end(2);
            } else {
                b.startStatement().startCall("afterChild");
                b.string(Boolean.toString(!operation.isVoid));
                if (operation.instruction != null) {
                    b.string("bci - " + operation.instruction.getInstructionLength());
                } else {
                    b.string("-1");
                }
                b.end(2);
            }

            if (operation.isCustom() && !operation.customModel.implicitTags.isEmpty()) {
                VariableElement tagConstants = lookupTagConstant(operation.customModel.implicitTags);
                if (tagConstants != null) {
                    buildEnd(b, model.tagOperation, tagConstants.getSimpleName().toString());
                }
            }

            return ex;
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

        private void createEndLocalsBlock(CodeTreeBuilder b, boolean isRoot) {
            b.startIf().string("operationData.numLocals > 0").end().startBlock();
            b.statement("maxLocals = Math.max(maxLocals, operationData.frameOffset + operationData.numLocals)");
            b.startFor().string("int index = 0; index < operationData.numLocals; index++").end().startBlock();
            b.statement("locals[operationData.locals[index] + LOCALS_OFFSET_END_BCI] = bci");
            if (!isRoot) {
                buildEmitInstruction(b, model.clearLocalInstruction, safeCastShort("locals[operationData.locals[index] + LOCALS_OFFSET_FRAME_INDEX]"));
            }
            b.end(); // for
            b.end(); // block
            b.statement("operationData.valid = false");
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
                emitCastOperationData(b, model.blockOperation, "operationSp - 1", "blockOperation");
                b.startIf().string("!blockOperation.producedValue").end().startBlock();
                buildEmit(b, model.loadNullOperation);
                b.end();
                buildEnd(b, model.blockOperation);
                emitCastOperationData(b, model.rootOperation, "rootOperationSp");
            } else {
                emitCastOperationData(b, model.rootOperation, "rootOperationSp");
                b.startIf().string("!operationData.producedValue").end().startBlock();
                buildEmit(b, model.loadNullOperation);
                b.end();
            }

            if (model.prolog != null || model.epilogExceptional != null || model.epilogReturn != null) {
                if (model.prolog != null) {
                    // Patch the end constants.
                    OperationModel prologOperation = model.prolog.operation;
                    List<InstructionImmediate> constantOperands = prologOperation.instruction.getImmediates(ImmediateKind.CONSTANT);
                    int endConstantsOffset = prologOperation.constantOperands.before().size();

                    for (OperationArgument operationArgument : model.prolog.operation.operationEndArguments) {
                        buildConstantOperandValidation(b, operationArgument.builderType(), operationArgument.name());
                    }

                    for (int i = 0; i < prologOperation.operationEndArguments.length; i++) {
                        InstructionImmediate immediate = constantOperands.get(endConstantsOffset + i);
                        b.statement(writeImmediate("bc", "operationData.prologBci", "constantPool.addConstant(" + prologOperation.operationEndArguments[i].name() + ")", immediate));
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
                    b.statement("doCreateExceptionHandler(0, bci, HANDLER_EPILOG_EXCEPTIONAL, -1, -1)");
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
                createEndLocalsBlock(b, true);
            }

            for (VariableElement e : ElementFilter.fieldsIn(abstractBytecodeNode.getEnclosedElements())) {
                b.defaultDeclaration(e.asType(), e.getSimpleName().toString() + "_");
            }

            b.statement("doEmitRoot()");
            b.startIf().string("parseSources").end().startBlock();
            CodeTree copyOf = CodeTreeBuilder.createBuilder().startStaticCall(type(Arrays.class), "copyOf").string("sourceInfo").string("sourceInfoIndex").end().build();
            b.startAssign("sourceInfo_").tree(copyOf).end();
            b.startAssign("sources_").string("sources").end();
            b.end();

            b.startIf().string("parseBytecodes").end().startBlock();

            b.startAssign("bytecodes_").startStaticCall(type(Arrays.class), "copyOf").string("bc").string("bci").end().end();
            b.startAssign("constants_").string("constantPool.toArray()").end();
            b.startAssign("handlers_").startStaticCall(type(Arrays.class), "copyOf").string("handlerTable").string("handlerTableSize").end().end();
            b.startAssign("sources_").string("sources").end();
            b.startAssign("numNodes_").string("numNodes").end();
            b.startAssign("locals_").string("locals == null ? " + EMPTY_INT_ARRAY + " : ").startStaticCall(type(Arrays.class), "copyOf").string("locals").string(
                            "localsTableIndex").end().end();
            b.end();

            if (model.enableTagInstrumentation) {
                b.startIf().string("tags != 0 && this.tagNodes != null").end().startBlock();
                b.startDeclaration(arrayOf(tagNode.asType()), "tagNodes_").string("this.tagNodes.toArray(TagNode[]::new)").end();

                b.declaration(tagNode.asType(), "tagTree_");

                b.startAssert().string("!this.tagRoots.isEmpty()").end();
                b.startIf().string("this.tagRoots.size() == 1").end().startBlock();
                b.startAssign("tagTree_").string("this.tagRoots.get(0)").end();
                b.end().startElseBlock();
                b.startAssign("tagTree_").startNew(tagNode.asType());
                b.string("0").string("-1");
                b.end().end();
                b.statement("tagTree_.children = tagTree_.insert(this.tagRoots.toArray(TagNode[]::new))");
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
            b.startIf().string("reparseReason != null").end().startBlock(); // {
            b.statement("result = builtNodes.get(operationData.index)");

            b.startIf().string("parseBytecodes").end().startBlock();
            b.declaration(abstractBytecodeNode.asType(), "oldBytecodeNode", "result.bytecode");
            b.statement("assert result.maxLocals == " + maxLocals());
            b.statement("assert result.nodes == this.nodes");
            b.statement("assert constants_.length == oldBytecodeNode.constants.length");
            b.startAssert();
            b.string("result.getFrameDescriptor().getNumberOfSlots() == ");
            buildFrameSize(b);
            b.end();

            if (model.enableYield) {
                /**
                 * Copy ContinuationRootNodes into new constant array *before* we update the new
                 * bytecode, otherwise a racy thread may read it as null
                 */
                b.startFor().type(continuationLocation.asType()).string(" continuationLocation : continuationLocations").end().startBlock();
                b.declaration(type(int.class), "constantPoolIndex", "continuationLocation.constantPoolIndex");
                b.startDeclaration(continuationRootNodeImpl.asType(), "continuationRootNode");
                b.cast(continuationRootNodeImpl.asType()).string("oldBytecodeNode.constants[constantPoolIndex]");
                b.end();

                b.startStatement().startCall("ACCESS.writeObject");
                b.string("constants_");
                b.string("constantPoolIndex");
                b.string("continuationRootNode");
                b.end(2);

                b.end();
            }

            b.end();

            if (model.enableYield) {
                b.startDeclaration(abstractBytecodeNode.asType(), "bytecodeNode");
            } else {
                b.startStatement();
            }
            b.startCall("result", "updateBytecode");
            for (VariableElement e : ElementFilter.fieldsIn(abstractBytecodeNode.getEnclosedElements())) {
                b.string(e.getSimpleName().toString() + "_");
            }
            b.string("this.reparseReason");
            if (model.enableYield) {
                b.string("continuationLocations");
            }
            b.end();
            b.end();

            b.startAssert().string("result.buildIndex == operationData.index").end();

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
                b.string("numLocals");
            }
            b.string("operationData.index");

            for (VariableElement e : ElementFilter.fieldsIn(abstractBytecodeNode.getEnclosedElements())) {
                b.string(e.getSimpleName().toString() + "_");
            }
            b.end(2);

            if (model.enableYield) {
                b.declaration(types.BytecodeNode, "bytecodeNode", "result.getBytecodeNode()");

                b.startFor().type(continuationLocation.asType()).string(" continuationLocation : continuationLocations").end().startBlock();
                b.declaration(type(int.class), "constantPoolIndex", "continuationLocation.constantPoolIndex");

                b.declaration(types.BytecodeLocation, "location");
                b.startIf().string("continuationLocation.bci == -1").end().startBlock();
                b.statement("location = null");
                b.end().startElseBlock();
                b.startAssign("location").string("bytecodeNode.getBytecodeLocation(continuationLocation.bci)").end();
                b.end(); // block

                b.startDeclaration(continuationRootNodeImpl.asType(), "continuationRootNode").startNew(continuationRootNodeImpl.asType());
                b.string("language");
                b.string("result.getFrameDescriptor()");
                b.string("result");
                b.string("continuationLocation.sp");
                b.string("location");
                b.end(2);

                b.startStatement().startCall("ACCESS.writeObject");
                b.string("constants_");
                b.string("constantPoolIndex");
                b.string("continuationRootNode");
                b.end(2);

                b.end();
            }

            b.startAssert().string("operationData.index <= numRoots").end();
            b.statement("builtNodes.set(operationData.index, result)");

            b.end(); // }

            b.statement("rootOperationSp = -1");

            b.startIf().string("savedState == null").end().startBlock(); // {
            b.lineComment("invariant: bc is null when no root node is being built");
            b.statement("bc = null");
            b.end().startElseBlock(); // } {
            for (CodeVariableElement state : builderState) {
                if (state != null) {
                    b.startAssign("this." + state.getName()).string("savedState." + state.getName()).end();
                }
            }
            b.end();

            b.startReturn().string("result").end();
            return ex;
        }

        private void buildFrameSize(CodeTreeBuilder b) {
            b.string("maxStackHeight + ").string(maxLocals());
        }

        private String maxLocals() {
            if (model.enableBlockScoping) {
                return "maxLocals + USER_LOCALS_START_INDEX";
            } else {
                return "numLocals + USER_LOCALS_START_INDEX";
            }
        }

        private void buildBegin(CodeTreeBuilder b, OperationModel operation, String... args) {
            b.startStatement().startCall("begin" + operation.name);
            for (String arg : args) {
                b.string(arg);
            }
            b.end(2);
        }

        private void buildEnd(CodeTreeBuilder b, OperationModel operation, String... args) {
            b.startStatement().startCall("end" + operation.name);
            for (String arg : args) {
                b.string(arg);
            }
            b.end(2);
        }

        private void buildEmit(CodeTreeBuilder b, OperationModel operation, String... args) {
            b.startStatement().startCall("emit" + operation.name);
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
            b.startFor().string("int i = operationSp - 1; i >= ", lowerLimit, "; i--").end().startBlock();

            b.startIf().string("operationStack[i].operation == ").tree(createOperationConstant(model.finallyHandlerOperation)).end().startBlock();
            b.startAssign("i").startParantheses();
            emitCastOperationDataUnchecked(b, model.finallyHandlerOperation, "i");
            b.end();
            b.string(".finallyOperationSp");
            b.end(); // assign
            b.statement("continue");
            b.end(); // if

            r.run();

            b.end(); // for
        }

        /**
         * Common case for a operation stack walks; walks until we hit the current root operation.
         */
        private void buildOperationStackWalk(CodeTreeBuilder b, Runnable r) {
            buildOperationStackWalk(b, "rootOperationSp", r);
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
            b.startFor().string("int i = ", lowerLimit, "; i < operationSp; i++").end().startBlock();

            b.startIf();
            b.string("operationStack[i].operation == ").tree(createOperationConstant(model.tryFinallyOperation));
            b.string(" || operationStack[i].operation == ").tree(createOperationConstant(model.tryCatchOtherwiseOperation));
            b.end().startBlock();

            if (!getDataClassName(model.tryFinallyOperation).equals(getDataClassName(model.tryCatchOtherwiseOperation))) {
                throw new AssertionError("TryFinally and TryCatchOtherwise operations have different data classes.");
            }
            b.startDeclaration(type(int.class), "finallyHandlerSp");
            b.startParantheses();
            emitCastOperationDataUnchecked(b, model.tryFinallyOperation, "i");
            b.end();
            b.string(".finallyHandlerSp");
            b.end();

            b.startIf().string("finallyHandlerSp != ", UNINIT).end().startBlock();
            b.statement("i = finallyHandlerSp - 1");
            b.statement("continue");
            b.end(); // if finallyHandlerSp set

            b.end(); // if finally try operation

            r.run();

            b.end(); // for
        }

        private void emitCastOperationData(CodeTreeBuilder b, OperationModel operation, String sp) {
            emitCastOperationData(b, operation, sp, "operationData");
        }

        private void emitCastOperationData(CodeTreeBuilder b, OperationModel operation, String sp, String localName) {
            b.startIf();
            b.string("!(operationStack[" + sp + "].data instanceof ");
            String dataClassName = getDataClassName(operation);
            b.string(dataClassName);
            b.string(" ").string(localName).string(")");
            b.end().startBlock();
            emitThrowAssertionError(b, "\"Data class " + dataClassName + " expected, but was \" + operationStack[" + sp + "].data");
            b.end();
        }

        private void emitCastCurrentOperationData(CodeTreeBuilder b, OperationModel operation) {
            b.startIf();
            b.string("!(operation.data instanceof ");
            String dataClassName = getDataClassName(operation);
            b.string(dataClassName);
            b.string(" ").string("operationData").string(")");
            b.end().startBlock();
            emitThrowAssertionError(b, "\"Data class " + dataClassName + " expected, but was \" + operation.data");
            b.end();
        }

        private void emitCastOperationDataUnchecked(CodeTreeBuilder b, OperationModel operation, String sp) {
            String dataClassName = getDataClassName(operation);
            b.string("(", dataClassName, ") operationStack[", sp, "].data");
        }

        /**
         * Produces code to emit finally handler(s) after the try block.
         *
         * For TryFinally, emits both regular and exceptional handlers; for TryCatchOtherwise, just
         * emits the regular handler.
         *
         * NB: each call to doEmitFinallyHandler must happen regardless of reachability so that the
         * frame and constant pool layouts are consistent across reparses.
         */
        private void emitFinallyHandlersAfterTry(CodeTreeBuilder b, OperationModel op, String finallyHandlerSp) {
            b.declaration(type(int.class), "handlerSp", "currentStackHeight + 1 /* reserve space for the exception */");
            b.statement("updateMaxStackHeight(handlerSp)");
            b.declaration(type(int.class), "exHandlerIndex", UNINIT);

            b.startIf().string("operationData.operationReachable").end().startBlock();
            b.lineComment("register exception table entry");
            b.statement("exHandlerIndex = doCreateExceptionHandler(operationData.tryStartBci, bci, HANDLER_CUSTOM, -operationData.handlerId, handlerSp)");
            b.end();

            b.lineComment("emit handler for normal completion case");
            b.statement("doEmitFinallyHandler(operationData, ", finallyHandlerSp, ")");
            b.lineComment("the operation was popped, so manually update reachability. try is reachable if neither it nor the finally handler exited early.");
            b.statement("operationData.tryReachable = operationData.tryReachable && this.reachable");

            b.startIf().string("this.reachable").end().startBlock();
            b.statement("operationData.endBranchFixupBci = bci + " + model.branchInstruction.findImmediate(ImmediateKind.BYTECODE_INDEX, "branch_target").offset());
            buildEmitInstruction(b, model.branchInstruction, new String[]{UNINIT});
            b.end();

            b.startIf().string("operationData.operationReachable").end().startBlock();
            b.lineComment("update exception table; force handler code to be reachable");
            b.statement("this.reachable = true");

            b.startStatement().startCall("patchHandlerTable");
            b.string("operationData.extraTableEntriesStart");
            b.string("operationData.extraTableEntriesEnd");
            b.string("operationData.handlerId");
            b.string("bci");
            b.string("handlerSp");
            b.end(2);

            b.startIf().string("exHandlerIndex != ", UNINIT).end().startBlock();
            b.statement("handlerTable[exHandlerIndex + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI] = bci");
            b.end();

            b.end(); // if operationReachable

            if (op.kind != OperationKind.TRY_CATCH_OTHERWISE) {
                b.lineComment("emit handler for exceptional case");
                b.statement("currentStackHeight = handlerSp");
                b.statement("doEmitFinallyHandler(operationData, " + finallyHandlerSp + ")");
                buildEmitInstruction(b, model.throwInstruction);
            }
        }

        /**
         * Produces code to patch the regular finally handler's branch over the exceptional handler.
         */
        private void emitFixFinallyBranchBci(CodeTreeBuilder b) {
            // The regular handler branches over the exceptional handler. Patch its bci.
            b.startIf().string("operationData.endBranchFixupBci != ", UNINIT).end().startBlock();
            b.statement(writeInt("bc", "operationData.endBranchFixupBci", "bci"));
            b.end();
        }

        private void buildEmitOperationInstruction(CodeTreeBuilder b, OperationModel operation, List<String> constantOperandIndices) {
            buildEmitOperationInstruction(b, operation, null, "operationSp", constantOperandIndices);
        }

        private void buildEmitOperationInstruction(CodeTreeBuilder b, OperationModel operation, String customChildBci, String sp, List<String> constantOperandIndices) {
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
                    emitCastCurrentOperationData(b, operation);
                    String local = model.usesBoxingElimination() ? "operationData.local" : "operationData";
                    List<String> immediates = new ArrayList<>();
                    immediates.add(local + ".frameIndex");
                    if (model.localAccessesNeedLocalIndex()) {
                        immediates.add(local + ".localIndex");
                    }
                    if (model.usesBoxingElimination()) {
                        immediates.add("operationData.childBci");
                    }
                    yield immediates.toArray(String[]::new);
                }
                case STORE_LOCAL_MATERIALIZED -> {
                    emitCastCurrentOperationData(b, operation);
                    String local = model.usesBoxingElimination() ? "operationData.local" : "operationData";
                    List<String> immediates = new ArrayList<>();
                    immediates.add(local + ".frameIndex");
                    immediates.add(local + ".rootIndex");
                    if (model.materializedLocalAccessesNeedLocalIndex()) {
                        immediates.add(local + ".localIndex");
                    }
                    if (model.usesBoxingElimination()) {
                        immediates.add("operationData.childBci");
                    }
                    yield immediates.toArray(String[]::new);
                }
                case LOAD_LOCAL_MATERIALIZED -> {
                    emitCastCurrentOperationData(b, operation);
                    List<String> immediates = new ArrayList<>();
                    immediates.add("operationData.frameIndex");
                    immediates.add("operationData.rootIndex");
                    if (model.materializedLocalAccessesNeedLocalIndex()) {
                        immediates.add("operationData.localIndex");
                    }
                    yield immediates.toArray(String[]::new);
                }
                case RETURN, LOAD_NULL -> new String[]{};
                case LOAD_ARGUMENT -> new String[]{safeCastShort(operation.getOperationBeginArgumentName(0))};
                case LOAD_CONSTANT -> new String[]{"constantPool.addConstant(" + operation.getOperationBeginArgumentName(0) + ")"};
                case YIELD -> {
                    b.declaration(type(short.class), "constantPoolIndex", "allocateContinuationConstant()");

                    b.declaration(type(int.class), "continuationBci");
                    b.startIf().string("reachable").end().startBlock();
                    b.statement("continuationBci = bci + " + operation.instruction.getInstructionLength());
                    b.end().startElseBlock();
                    b.statement("continuationBci = -1");
                    b.end();

                    b.startStatement().startCall("continuationLocations.add");
                    b.startNew(continuationLocation.asType()).string("constantPoolIndex").string("continuationBci").string("currentStackHeight").end();
                    b.end(2); // statement + call
                    b.end();
                    yield new String[]{"constantPoolIndex"};
                }
                case CUSTOM, CUSTOM_INSTRUMENTATION -> buildCustomInitializer(b, operation, operation.instruction, customChildBci, sp, constantOperandIndices);
                case CUSTOM_SHORT_CIRCUIT -> throw new AssertionError("Tried to emit a short circuit instruction directly. These operations should only be emitted implicitly.");
                default -> throw new AssertionError("Reached an operation " + operation.name + " that cannot be initialized. This is a bug in the Bytecode DSL processor.");
            };
            buildEmitInstruction(b, operation.instruction, args);
        }

        private void buildEmitLabel(CodeTreeBuilder b, OperationModel operation) {
            b.startAssign("BytecodeLabelImpl labelImpl").string("(BytecodeLabelImpl) " + operation.getOperationBeginArgumentName(0)).end();

            b.startIf().string("labelImpl.isDefined()").end().startBlock();
            b.startThrow().startCall("failState").doubleQuote("BytecodeLabel already emitted. Each label must be emitted exactly once.").end().end();
            b.end();

            b.startIf().string("labelImpl.declaringOp != operationStack[operationSp - 1].sequenceNumber").end().startBlock();
            b.startThrow().startCall("failState").doubleQuote("BytecodeLabel must be emitted inside the same operation it was created in.").end().end();
            b.end();

            b.startIf().string("operationStack[operationSp - 1].data instanceof " + getDataClassName(model.blockOperation) + " blockData").end().startBlock();
            b.startAssert().string("this.currentStackHeight == blockData.startStackHeight").end();
            b.end().startElseBlock();
            b.startAssert().string("operationStack[operationSp - 1].data instanceof " + getDataClassName(model.rootOperation)).end();
            b.startAssert().string("this.currentStackHeight == 0").end();
            b.end();

            b.startStatement().startCall("resolveUnresolvedLabel");
            b.string("labelImpl");
            b.string("currentStackHeight");
            b.end(2);
        }

        private void buildEmitBranch(CodeTreeBuilder b, OperationModel operation) {
            b.startAssign("BytecodeLabelImpl labelImpl").string("(BytecodeLabelImpl) " + operation.getOperationBeginArgumentName(0)).end();

            b.declaration(type(int.class), "declaringOperationSp", UNINIT);
            buildOperationStackWalk(b, () -> {
                b.startIf().string("operationStack[i].sequenceNumber == labelImpl.declaringOp").end().startBlock();
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
            b.startIf().string("operationStack[declaringOperationSp].data instanceof " + getDataClassName(model.blockOperation) + " blockData").end().startBlock();
            b.startAssign("targetStackHeight").string("blockData.startStackHeight").end();
            b.end().startElseBlock();
            b.startAssert().string("operationStack[declaringOperationSp].data instanceof " + getDataClassName(model.rootOperation)).end();
            b.startAssign("targetStackHeight").string("0").end();
            b.end();

            b.statement("beforeEmitBranch(declaringOperationSp)");

            /**
             * If the label sp doesn't match the current sp, we need to pop before branching.
             */
            b.lineComment("Pop any extra values off the stack before branching.");
            b.declaration(type(int.class), "stackHeightBeforeBranch", "currentStackHeight");
            b.startWhile().string("targetStackHeight != currentStackHeight").end().startBlock();
            buildEmitInstruction(b, model.popInstruction, emitPopArguments("-1"));
            b.end();
            b.lineComment("If the branch is not taken (e.g., control branches over it) the values are still on the stack.");
            b.statement("currentStackHeight = stackHeightBeforeBranch");

            b.startIf().string("this.reachable").end().startBlock();
            /**
             * Mark the branch target as uninitialized. Add this location to a work list to be
             * processed once the label is defined.
             */
            b.startStatement().startCall("registerUnresolvedLabel");
            b.string("labelImpl");
            b.string("bci + " + model.branchInstruction.getImmediate(ImmediateKind.BYTECODE_INDEX).offset());
            b.end(2);
            b.end(); // if reachable

            buildEmitInstruction(b, model.branchInstruction, UNINIT);
        }

        private void buildEmitLoadException(CodeTreeBuilder b, OperationModel operation) {
            b.declaration(type(short.class), "exceptionStackHeight", UNINIT);
            b.string("loop: ");
            buildOperationStackWalk(b, () -> {
                b.startSwitch().string("operationStack[i].operation").end().startBlock();

                b.startCase().tree(createOperationConstant(model.tryCatchOperation)).end();
                b.startBlock();
                emitCastOperationData(b, model.tryCatchOperation, "i");
                b.startIf().string("operationStack[i].childCount == 1").end().startBlock();
                b.statement("exceptionStackHeight = operationData.stackHeight");
                b.statement("break loop");
                b.end();
                b.statement("break");
                b.end(); // case TryCatch

                b.startCase().tree(createOperationConstant(model.tryCatchOtherwiseOperation)).end();
                b.startBlock();
                emitCastOperationData(b, model.tryCatchOtherwiseOperation, "i");
                b.startIf().string("operationStack[i].childCount == 1").end().startBlock();
                b.statement("exceptionStackHeight = operationData.stackHeight");
                b.statement("break loop");
                b.end();
                b.statement("break");
                b.end(); // case TryCatchOtherwise

                b.end(); // switch
            });

            b.startIf().string("exceptionStackHeight == ", UNINIT).end().startBlock();
            b.startThrow().startCall("failState").doubleQuote("LoadException can only be used in the catch operation of a TryCatch/TryCatchOtherwise operation in the current root.").end().end();
            b.end();

            buildEmitInstruction(b, operation.instruction, "exceptionStackHeight");
        }

        private CodeExecutableElement createValidateRootOperationBegin() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "validateRootOperationBegin");

            CodeTreeBuilder b = ex.createBuilder();

            b.startIf().string("rootOperationSp == -1").end().startBlock(); // {
            b.startThrow().startCall("failState");
            b.doubleQuote("Unexpected operation emit - no root operation present. Did you forget a beginRoot()?");
            b.end(2);
            b.end(); // }

            return ex;
        }

        private CodeExecutableElement createGetCurrentRootOperationData() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), dataClasses.get(model.rootOperation).asType(), "getCurrentRootOperationData");

            CodeTreeBuilder b = ex.createBuilder();
            b.startStatement().startCall("validateRootOperationBegin").end(2);

            emitCastOperationData(b, model.rootOperation, "rootOperationSp", "rootOperationData");
            b.startReturn().string("rootOperationData").end();

            return ex;
        }

        private CodeExecutableElement createEmit(OperationModel operation) {
            Modifier visibility = operation.isInternal ? PRIVATE : PUBLIC;
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(visibility), type(void.class), "emit" + operation.name);
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

            if (operation.constantOperands != null && operation.constantOperands.hasConstantOperands()) {
                int index = 0;
                for (ConstantOperandModel operand : operation.constantOperands.before()) {
                    buildConstantOperandValidation(b, operand.type(), operation.getOperationBeginArgumentName(index++));
                }
                index = 0;
                for (ConstantOperandModel operand : operation.constantOperands.after()) {
                    buildConstantOperandValidation(b, operand.type(), operation.getOperationEndArgumentName(index++));
                }
            }

            List<String> constantOperandIndices = emitConstantOperands(b, operation);

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

            if (operation.kind == OperationKind.LOAD_LOCAL) {
                emitValidateLocalScope(b, operation);
            }

            // emit the instruction
            switch (operation.kind) {
                case LABEL -> buildEmitLabel(b, operation);
                case BRANCH -> buildEmitBranch(b, operation);
                case LOAD_EXCEPTION -> buildEmitLoadException(b, operation);
                default -> {
                    if (operation.instruction == null) {
                        throw new AssertionError("operation did not have instruction");
                    }
                    buildEmitOperationInstruction(b, operation, constantOperandIndices);
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

            b.startStatement().startCall("afterChild");
            b.string("" + !operation.isVoid);
            b.string(operation.instruction != null ? "bci - " + operation.instruction.getInstructionLength() : "-1");
            b.end(2);

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

            int numConstantOperands = operation.numConstantOperandsBefore();
            if (numConstantOperands == 0) {
                return List.of();
            }

            List<String> result = new ArrayList<>(numConstantOperands);
            for (int i = 0; i < numConstantOperands; i++) {
                /**
                 * Eagerly allocate space for the constants. Even if the node is not emitted (e.g.,
                 * it's a disabled instrumentation), we need the constant pool to be stable.
                 */
                String constantPoolIndex = operation.getConstantOperandBeforeName(i) + "Index";
                b.startDeclaration(type(int.class), constantPoolIndex);
                buildAddArgumentConstant(b, operation.operationBeginArguments[i]);
                b.end();
                result.add(constantPoolIndex);
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
            int numConstantOperandsBefore = operation.numConstantOperandsBefore();
            int numConstantOperandsAfter = operation.numConstantOperandsAfter();
            int numConstantOperands = numConstantOperandsBefore + numConstantOperandsAfter;
            if (numConstantOperands == 0) {
                return List.of();
            }

            boolean inEmit = !operation.hasChildren();
            List<String> result = new ArrayList<>(numConstantOperands);
            for (int i = 0; i < numConstantOperandsBefore; i++) {
                if (inEmit) {
                    String variable = operation.getConstantOperandBeforeName(i) + "Index";
                    b.startDeclaration(type(int.class), variable);
                    buildAddArgumentConstant(b, operation.operationBeginArguments[i]);
                    b.end();
                    result.add(variable);
                } else {
                    result.add("operationData.constants[" + i + "]");
                }
            }
            for (int i = 0; i < numConstantOperandsAfter; i++) {
                if (model.prolog != null && operation == model.prolog.operation) {
                    /**
                     * Special case: when emitting the prolog in beginRoot, end constants are not
                     * yet known. They will be patched in endRoot.
                     */
                    result.add(UNINIT);
                } else {
                    String variable = operation.getConstantOperandAfterName(i) + "Index";
                    b.startDeclaration(type(int.class), variable);
                    buildAddArgumentConstant(b, operation.operationEndArguments[i]);
                    b.end();
                    result.add(variable);
                }

            }
            return result;
        }

        private String[] buildCustomInitializer(CodeTreeBuilder b, OperationModel operation, InstructionModel instruction, String customChildBci, String sp, List<String> constantOperandIndices) {
            if (operation.kind == OperationKind.CUSTOM_SHORT_CIRCUIT) {
                throw new AssertionError("short circuit operations should not be emitted directly.");
            }

            if (instruction.signature.isVariadic) {
                // Before emitting a variadic instruction, we need to emit instructions to merge all
                // of the operands on the stack into one array.
                b.statement("doEmitVariadic(operation.childCount - " + (instruction.signature.dynamicOperandCount - 1) + ")");
            }

            if (customChildBci != null && operation.numDynamicOperands() > 1) {
                throw new AssertionError("customChildBci can only be used with a single child.");
            }

            boolean inEmit = !operation.hasChildren();

            if (!inEmit && !operation.isTransparent()) {
                // make "operationData" available for endX methods.
                if (sp.equals("operationSp")) {
                    emitCastCurrentOperationData(b, operation);
                } else {
                    emitCastOperationData(b, operation, sp);
                }
            }

            List<InstructionImmediate> immediates = instruction.getImmediates();
            String[] args = new String[immediates.size()];

            int childBciIndex = 0;
            int constantIndex = 0;
            for (int i = 0; i < immediates.size(); i++) {
                InstructionImmediate immediate = immediates.get(i);
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
                                yield "operationData.childBci";
                            } else {
                                String childBci = "childBci" + childBciIndex;
                                b.declaration(type(int.class), childBci, "operationData.childBcis[" + childBciIndex + "]");
                                childBciIndex++;
                                yield childBci;
                            }
                        }
                    }
                    case CONSTANT -> constantOperandIndices.get(constantIndex++);
                    case NODE_PROFILE -> "allocateNode()";
                    case TAG_NODE -> "node";
                    case FRAME_INDEX, LOCAL_INDEX, LOCAL_ROOT, SHORT, BRANCH_PROFILE, STACK_POINTER -> throw new AssertionError(
                                    "Operation " + operation.name + " takes an immediate " + immediate.name() + " with unexpected kind " + immediate.kind() +
                                                    ". This is a bug in the Bytecode DSL processor.");
                };
            }

            return args;
        }

        private void buildAddArgumentConstant(CodeTreeBuilder b, OperationArgument argument) {
            b.startCall("constantPool.addConstant");
            if (ElementUtils.typeEquals(argument.builderType(), argument.constantType())) {
                b.string(argument.name());
            } else {
                b.startStaticCall(argument.constantType(), "constantOf");
                if (ElementUtils.typeEquals(argument.constantType(), types.MaterializedLocalAccessor)) {
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
        }

        private CodeExecutableElement createBeforeChild() {
            enum BeforeChildKind {
                TRANSPARENT,
                SHORT_CIRCUIT,
                UPDATE_REACHABLE,
                EXCEPTION_HANDLER,
                DEFAULT,
            }

            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "beforeChild");
            CodeTreeBuilder b = ex.createBuilder();

            b.startIf().string("operationSp == 0").end().startBlock();
            b.statement("return");
            b.end();

            b.statement("int childIndex = operationStack[operationSp - 1].childCount");
            b.startSwitch().string("operationStack[operationSp - 1].operation").end().startBlock();

            Map<BeforeChildKind, List<OperationModel>> groupedOperations = model.getOperations().stream().filter(OperationModel::hasChildren).collect(deterministicGroupingBy(op -> {
                if (op.isTransparent && (op.isVariadic || op.numDynamicOperands() > 1)) {
                    return BeforeChildKind.TRANSPARENT;
                } else if (op.kind == OperationKind.CUSTOM_SHORT_CIRCUIT) {
                    return BeforeChildKind.SHORT_CIRCUIT;
                } else if (op.kind == OperationKind.IF_THEN_ELSE ||
                                op.kind == OperationKind.IF_THEN ||
                                op.kind == OperationKind.CONDITIONAL ||
                                op.kind == OperationKind.TRY_FINALLY) {
                    return BeforeChildKind.UPDATE_REACHABLE;
                } else if (op.kind == OperationKind.TRY_CATCH ||
                                op.kind == OperationKind.TRY_CATCH_OTHERWISE) {
                    return BeforeChildKind.EXCEPTION_HANDLER;
                } else {
                    return BeforeChildKind.DEFAULT;
                }
            }));

            // Pop any value produced by a transparent operation's child.
            if (groupedOperations.containsKey(BeforeChildKind.TRANSPARENT)) {
                List<OperationModel> models = groupedOperations.get(BeforeChildKind.TRANSPARENT);
                for (List<OperationModel> grouped : groupByDataClass(models)) {
                    for (OperationModel op : grouped) {
                        b.startCase().tree(createOperationConstant(op)).end();
                    }
                    b.startBlock();
                    emitCastOperationData(b, grouped.get(0), "operationSp - 1");
                    b.startIf().string("operationData.producedValue").end().startBlock();
                    buildEmitInstruction(b, model.popInstruction, emitPopArguments("operationData.childBci"));
                    b.end();
                    b.statement("break");
                    b.end();
                }
            }

            // Perform check after each child of a short-circuit operation.
            if (groupedOperations.containsKey(BeforeChildKind.SHORT_CIRCUIT)) {
                for (OperationModel op : groupedOperations.get(BeforeChildKind.SHORT_CIRCUIT)) {
                    b.startCase().tree(createOperationConstant(op)).end().startBlock();

                    ShortCircuitInstructionModel shortCircuitModel = op.instruction.shortCircuitModel;

                    emitCastOperationData(b, op, "operationSp - 1");
                    // Only emit the boolean check between consecutive children.
                    b.startIf().string("childIndex != 0").end().startBlock();

                    // If this operation has a converter, convert the value.
                    if (shortCircuitModel.convertsOperands()) {
                        if (shortCircuitModel.duplicatesOperandOnStack()) {
                            buildEmitInstruction(b, model.dupInstruction);
                        }
                        buildEmitBooleanConverterInstruction(b, op.instruction);
                    }

                    // Remember the short circuit instruction's bci so we can patch the branch bci.
                    b.startIf().string("this.reachable").end().startBlock();
                    b.statement("operationData.branchFixupBcis.add(bci + " + op.instruction.getImmediate("branch_target").offset() + ")");
                    b.end();

                    b.statement("operationData.shortCircuitBci = bci");

                    // Emit the boolean check.
                    buildEmitInstruction(b, op.instruction, emitShortCircuitArguments(op.instruction));

                    b.end(); // childIndex != 0

                    b.statement("break");
                    b.end();
                }
            }

            // Update reachability for control-flow operations.
            if (groupedOperations.containsKey(BeforeChildKind.UPDATE_REACHABLE)) {
                for (OperationModel op : groupedOperations.get(BeforeChildKind.UPDATE_REACHABLE)) {
                    b.startCase().tree(createOperationConstant(op)).end();
                }
                b.startCaseBlock();

                b.startIf().string("childIndex >= 1").end().startBlock();
                b.statement("updateReachable()");
                b.end();

                b.statement("break");
                b.end();
            }

            List<OperationModel> exceptionHandlerOperations = groupedOperations.get(BeforeChildKind.EXCEPTION_HANDLER);
            if (exceptionHandlerOperations == null || exceptionHandlerOperations.size() != 2) {
                throw new AssertionError("Expected exactly 2 exception handler operations, but a different number was found.");
            }
            for (OperationModel op : exceptionHandlerOperations) {
                b.startCase().tree(createOperationConstant(op)).end();

                b.startBlock();
                emitCastOperationData(b, op, "operationSp - 1");
                b.startIf().string("childIndex == 1").end().startBlock();
                b.statement("updateReachable()");
                b.lineComment("The exception dispatch logic pushes the exception onto the stack.");
                b.statement("currentStackHeight = currentStackHeight + 1");
                b.statement("updateMaxStackHeight(currentStackHeight)");

                b.end(); // if

                b.statement("break");
                b.end();
            }

            // Do nothing for every other operation.
            if (groupedOperations.containsKey(BeforeChildKind.DEFAULT)) {
                for (OperationModel op : groupedOperations.get(BeforeChildKind.DEFAULT)) {
                    b.startCase().tree(createOperationConstant(op)).end();
                }
                b.startCaseBlock();
                b.statement("break");
                b.end();
            }

            b.caseDefault();
            b.startCaseBlock();
            emitThrowAssertionError(b, "\"beforeChild should not be called on an operation with no children.\"");
            b.end();

            b.end(); // switch

            return ex;
        }

        private Collection<List<OperationModel>> groupByDataClass(List<OperationModel> models) {
            return models.stream().collect(deterministicGroupingBy((m) -> getDataClassName(m))).values();
        }

        private void buildEmitBooleanConverterInstruction(CodeTreeBuilder b, InstructionModel shortCircuitInstruction) {
            InstructionModel booleanConverter = shortCircuitInstruction.shortCircuitModel.booleanConverterInstruction();

            List<InstructionImmediate> immediates = booleanConverter.getImmediates();
            String[] args = new String[immediates.size()];
            for (int i = 0; i < args.length; i++) {
                InstructionImmediate immediate = immediates.get(i);
                args[i] = switch (immediate.kind()) {
                    case BYTECODE_INDEX -> {
                        if (shortCircuitInstruction.shortCircuitModel.producesBoolean()) {
                            b.statement("int childBci = operationData.childBci");
                            b.startAssert();
                            b.string("childBci != " + UNINIT);
                            b.end();
                        } else {
                            b.lineComment("Boxing elimination not supported for converter operations if the value is returned.");
                            b.statement("int childBci = -1");
                        }
                        yield "childBci";
                    }
                    case NODE_PROFILE -> "allocateNode()";
                    default -> throw new AssertionError(String.format("Boolean converter instruction had unexpected encoding: %s", immediates));
                };
            }
            buildEmitInstruction(b, booleanConverter, args);
        }

        private CodeExecutableElement createAfterChild() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "afterChild");
            ex.addParameter(new CodeVariableElement(type(boolean.class), "producedValue"));
            ex.addParameter(new CodeVariableElement(type(int.class), "childBci"));
            CodeTreeBuilder b = ex.createBuilder();

            b.startIf().string("operationSp == 0").end().startBlock();
            b.statement("return");
            b.end();

            b.statement("int childIndex = operationStack[operationSp - 1].childCount");

            b.startSwitch().string("operationStack[operationSp - 1].operation").end().startBlock();

            Map<Boolean, List<OperationModel>> operationsByTransparency = model.getOperations().stream() //
                            .filter(OperationModel::hasChildren).collect(Collectors.partitioningBy(OperationModel::isTransparent));

            for (List<OperationModel> operations : groupByDataClass(operationsByTransparency.get(true))) {
                // First, do transparent operations (grouped).
                for (OperationModel op : operations) {
                    b.startCase().tree(createOperationConstant(op)).end();
                }
                b.startBlock();

                emitCastOperationData(b, operations.get(0), "operationSp - 1");
                b.statement("operationData.producedValue = producedValue");
                b.statement("operationData.childBci = childBci");
                b.statement("break");
                b.end();
            }

            // Then, do non-transparent operations (separately).
            for (OperationModel op : operationsByTransparency.get(false)) {
                b.startCase().tree(createOperationConstant(op)).end().startBlock();
                /**
                 * Ensure the stack balances. If a value was expected, assert that the child
                 * produced a value. If a value was not expected but the child produced one, pop it.
                 */
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
                        b.string("\"Operation " + op.name + " expected a value-producing child at position \"",
                                        " + childIndex + ",
                                        "\", but a void one was provided.\"");
                        b.end(3);
                    } else if (valueChildren.isEmpty()) {
                        // Simplification: each child should not be value producing.
                        b.startIf().string("producedValue").end().startBlock();
                        buildEmitInstruction(b, model.popInstruction, emitPopArguments("childBci"));
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
                        b.string("\"Operation " + op.name + " expected a value-producing child at position \"",
                                        " + childIndex + ",
                                        "\", but a void one was provided.\"");
                        b.end(3);

                        b.startElseIf();
                        b.string("(");
                        for (int i = 0; i < nonValueChildren.size(); i++) {
                            if (i != 0) {
                                b.string(" || ");
                            }
                            String operator = (op.isVariadic && nonValueChildren.get(i) == op.dynamicOperands.length - 1) ? ">=" : "==";
                            b.string("childIndex " + operator + " " + nonValueChildren.get(i));
                        }
                        b.string(") && producedValue");
                        b.end().startBlock();
                        buildEmitInstruction(b, model.popInstruction, emitPopArguments("childBci"));
                        b.end();
                    }
                }

                switch (op.kind) {
                    case ROOT:
                        break;
                    case TAG:
                        emitCastOperationData(b, op, "operationSp - 1");
                        b.statement("operationData.producedValue = producedValue");
                        b.statement("operationData.childBci = childBci");
                        break;
                    case RETURN:
                        emitCastOperationData(b, op, "operationSp - 1");
                        b.statement("operationData.producedValue = producedValue");
                        b.statement("operationData.childBci = childBci");
                        break;
                    case IF_THEN:
                        emitCastOperationData(b, op, "operationSp - 1");
                        b.startIf().string("childIndex == 0").end().startBlock();
                        b.startIf().string("reachable").end().startBlock();
                        b.statement("operationData.falseBranchFixupBci = bci + " + model.branchFalseInstruction.findImmediate(ImmediateKind.BYTECODE_INDEX, "branch_target").offset());
                        b.end();
                        buildEmitInstruction(b, model.branchFalseInstruction, emitBranchFalseArguments(model.branchFalseInstruction));
                        b.end().startElseBlock();
                        b.statement("int toUpdate = operationData.falseBranchFixupBci");
                        b.startIf().string("toUpdate != ", UNINIT).end().startBlock();
                        b.statement(writeInt("bc", "toUpdate", "bci"));
                        b.end();
                        b.end();
                        break;
                    case IF_THEN_ELSE:
                        emitCastOperationData(b, op, "operationSp - 1");
                        b.startIf().string("childIndex == 0").end().startBlock();
                        b.startIf().string("reachable").end().startBlock();
                        b.statement("operationData.falseBranchFixupBci = bci + " + model.branchFalseInstruction.findImmediate(ImmediateKind.BYTECODE_INDEX, "branch_target").offset());
                        b.end();
                        buildEmitInstruction(b, model.branchFalseInstruction, emitBranchFalseArguments(model.branchFalseInstruction));
                        b.end().startElseIf().string("childIndex == 1").end().startBlock();
                        b.startIf().string("reachable").end().startBlock();
                        b.statement("operationData.endBranchFixupBci = bci + " + model.branchInstruction.getImmediate(ImmediateKind.BYTECODE_INDEX).offset());
                        b.end();
                        buildEmitInstruction(b, model.branchInstruction, new String[]{UNINIT});
                        b.statement("int toUpdate = operationData.falseBranchFixupBci");
                        b.startIf().string("toUpdate != ", UNINIT).end().startBlock();
                        b.statement(writeInt("bc", "toUpdate", "bci"));
                        b.end();
                        b.end().startElseBlock();
                        b.statement("int toUpdate = operationData.endBranchFixupBci");
                        b.startIf().string("toUpdate != ", UNINIT).end().startBlock();
                        b.statement(writeInt("bc", "toUpdate", "bci"));
                        b.end();
                        b.end();
                        break;
                    case CONDITIONAL:
                        emitCastOperationData(b, op, "operationSp - 1");
                        b.startIf().string("childIndex == 0").end().startBlock();
                        if (model.usesBoxingElimination()) {
                            buildEmitInstruction(b, model.dupInstruction);
                        }
                        b.startIf().string("reachable").end().startBlock();
                        b.statement("operationData.falseBranchFixupBci = bci + " + model.branchFalseInstruction.findImmediate(ImmediateKind.BYTECODE_INDEX, "branch_target").offset());
                        b.end();
                        buildEmitInstruction(b, model.branchFalseInstruction, emitBranchFalseArguments(model.branchFalseInstruction));

                        b.end().startElseIf().string("childIndex == 1").end().startBlock();
                        if (model.usesBoxingElimination()) {
                            b.statement("operationData.child0Bci = childBci");
                        }
                        b.startIf().string("reachable").end().startBlock();
                        b.statement("operationData.endBranchFixupBci = bci + " + model.branchInstruction.getImmediate(ImmediateKind.BYTECODE_INDEX).offset());
                        buildEmitInstruction(b, model.branchInstruction, new String[]{UNINIT});
                        // we have to adjust the stack for the third child
                        b.end();
                        b.statement("currentStackHeight -= 1");

                        b.statement("int toUpdate = operationData.falseBranchFixupBci");
                        b.startIf().string("toUpdate != ", UNINIT).end().startBlock();
                        b.statement(writeInt("bc", "toUpdate", "bci"));
                        b.end();
                        b.end().startElseBlock();
                        if (model.usesBoxingElimination()) {
                            b.statement("operationData.child1Bci = childBci");
                        }
                        b.statement("int toUpdate = operationData.endBranchFixupBci");
                        b.startIf().string("toUpdate != ", UNINIT).end().startBlock();
                        b.statement(writeInt("bc", "toUpdate", "bci"));
                        b.end();
                        b.end();
                        break;
                    case WHILE:
                        InstructionImmediate branchTarget = model.branchFalseInstruction.findImmediate(ImmediateKind.BYTECODE_INDEX, "branch_target");
                        emitCastOperationData(b, op, "operationSp - 1");
                        b.startIf().string("childIndex == 0").end().startBlock();
                        b.startIf().string("reachable").end().startBlock();
                        b.statement("operationData.endBranchFixupBci = bci + " + branchTarget.offset());
                        b.end();
                        buildEmitInstruction(b, model.branchFalseInstruction, emitBranchFalseArguments(model.branchFalseInstruction));
                        b.end().startElseBlock();
                        b.statement("int toUpdate = operationData.endBranchFixupBci");
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
                        String readBranchProfile = readInt("bc", "toUpdate + " + offset + " /* loop branch profile */");
                        buildEmitInstruction(b, model.branchBackwardInstruction, new String[]{"operationData.whileStartBci", readBranchProfile});
                        b.statement(writeInt("bc", "toUpdate", "bci"));
                        b.end();

                        b.end();
                        break;
                    case TRY_CATCH:
                        emitCastOperationData(b, op, "operationSp - 1");
                        b.startIf().string("childIndex == 0").end().startBlock();
                        b.startIf().string("operationData.operationReachable").end().startBlock();
                        b.declaration(type(int.class), "tryEndBci", "bci");

                        b.startIf().string("operationData.tryReachable").end().startBlock();
                        b.statement("operationData.endBranchFixupBci = bci + " + model.branchInstruction.getImmediate(ImmediateKind.BYTECODE_INDEX).offset());
                        buildEmitInstruction(b, model.branchInstruction, new String[]{UNINIT});
                        b.end(); // if tryReachable

                        b.declaration(type(int.class), "handlerSp", "currentStackHeight + 1");
                        b.startStatement().startCall("patchHandlerTable");
                        b.string("operationData.extraTableEntriesStart");
                        b.string("operationData.extraTableEntriesEnd");
                        b.string("operationData.handlerId");
                        b.string("bci");
                        b.string("handlerSp");
                        b.end(2);

                        b.statement("doCreateExceptionHandler(operationData.tryStartBci, tryEndBci, HANDLER_CUSTOM, bci, handlerSp)");

                        b.end(); // if operationReachable
                        b.end();

                        b.startElseIf().string("childIndex == 1").end().startBlock();
                        b.lineComment("pop the exception");
                        buildEmitInstruction(b, model.popInstruction, emitPopArguments("-1"));
                        emitFixFinallyBranchBci(b);
                        b.end();
                        break;
                    case TRY_FINALLY:
                        break;
                    case TRY_CATCH_OTHERWISE:
                        emitCastOperationData(b, op, "operationSp - 1");
                        b.startIf().string("childIndex == 0").end().startBlock();
                        emitFinallyHandlersAfterTry(b, op, "operationSp - 1");
                        b.end().startElseBlock();
                        b.lineComment("pop the exception");
                        buildEmitInstruction(b, model.popInstruction, emitPopArguments("-1"));
                        emitFixFinallyBranchBci(b);
                        b.end();

                        break;
                    case STORE_LOCAL:
                    case STORE_LOCAL_MATERIALIZED:
                        if (model.usesBoxingElimination()) {
                            emitCastOperationData(b, op, "operationSp - 1");
                            b.statement("operationData.childBci = childBci");
                        } else {
                            // no operand to encode
                        }
                        break;
                    case CUSTOM:
                    case CUSTOM_INSTRUMENTATION:
                        int immediateIndex = 0;
                        boolean elseIf = false;
                        boolean operationDataEmitted = false;
                        for (int valueIndex = 0; valueIndex < op.instruction.signature.dynamicOperandCount; valueIndex++) {
                            if (op.instruction.needsBoxingElimination(model, valueIndex)) {
                                if (!operationDataEmitted) {
                                    emitCastOperationData(b, op, "operationSp - 1");
                                    operationDataEmitted = true;
                                }
                                elseIf = b.startIf(elseIf);
                                b.string("childIndex == " + valueIndex).end().startBlock();
                                b.statement("operationData.childBcis[" + immediateIndex++ + "] = childBci");
                                b.end();
                            }
                        }
                        break;
                    case CUSTOM_SHORT_CIRCUIT:
                        emitCastOperationData(b, op, "operationSp - 1");
                        b.statement("operationData.childBci = childBci");
                        break;
                }

                b.statement("break");
                b.end();
            }

            b.end();

            b.statement("operationStack[operationSp - 1].childCount = childIndex + 1");

            return ex;
        }

        private String[] emitShortCircuitArguments(InstructionModel instruction) {
            List<InstructionImmediate> immedates = instruction.getImmediates();
            String[] branchArguments = new String[immedates.size()];
            for (int index = 0; index < branchArguments.length; index++) {
                InstructionImmediate immediate = immedates.get(index);
                branchArguments[index] = switch (immediate.kind()) {
                    case BYTECODE_INDEX -> UNINIT;
                    case BRANCH_PROFILE -> "allocateBranchProfile()";
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
                    case BRANCH_PROFILE -> "allocateBranchProfile()";
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
                    case BYTECODE_INDEX -> (index == 0) ? "operationData.thenReachable ? operationData.child0Bci : -1"
                                    : "operationData.elseReachable ? operationData.child1Bci : -1";
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
            b.statement("nameIndex = constantPool.addConstant(name)");
            b.end();

            b.declaration(type(int.class), "infoIndex", "-1");
            b.startIf().string("info != null").end().startBlock();
            b.statement("infoIndex = constantPool.addConstant(info)");
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
                b.statement("locals[tableIndex + LOCALS_OFFSET_START_BCI] = bci");
                b.lineComment("will be patched later at the end of the block");
                b.statement("locals[tableIndex + LOCALS_OFFSET_END_BCI] = -1");
                b.statement("locals[tableIndex + LOCALS_OFFSET_LOCAL_INDEX] = localIndex");
                b.statement("locals[tableIndex + LOCALS_OFFSET_FRAME_INDEX] = frameIndex");
            }
            b.statement("locals[tableIndex + LOCALS_OFFSET_NAME] = nameIndex");
            b.statement("locals[tableIndex + LOCALS_OFFSET_INFO] = infoIndex");

            b.statement("return tableIndex");
            return ex;
        }

        private CodeExecutableElement createAllocateLocalsTableEntry() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(int.class), "allocateLocalsTableEntry");
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("int result = localsTableIndex");
            b.startIf().string("locals == null").end().startBlock();
            b.startAssert().string("result == 0").end();
            b.startAssign("locals").startNewArray(arrayOf(type(int.class)), CodeTreeBuilder.singleString("LOCALS_LENGTH * 8")).end().end();
            b.end().startElseIf().string("result + LOCALS_LENGTH > locals.length").end().startBlock();
            b.startAssign("locals").startStaticCall(type(Arrays.class), "copyOf");
            b.string("locals");
            b.string("Math.max(result + LOCALS_LENGTH, locals.length * 2)");
            b.end(2); // assign, static call
            b.end(); // if block
            b.statement("localsTableIndex += LOCALS_LENGTH");
            b.statement("return result");
            return ex;
        }

        private CodeExecutableElement ensureDoEmitInstructionCreated(InstructionModel instruction) {
            InstructionEncoding encoding = instruction.getInstructionEncoding();
            return doEmitInstructionMethods.computeIfAbsent(encoding, (length) -> createDoEmitInstruction(instruction));
        }

        private CodeExecutableElement createDoEmitInstruction(InstructionModel representativeInstruction) {
            // Give each method a unique name so that we don't accidentally use the wrong overload.
            StringBuilder methodName = new StringBuilder("doEmitInstruction");
            for (InstructionImmediate immediate : representativeInstruction.immediates) {
                methodName.append(switch (immediate.kind().width) {
                    case BYTE -> "B";
                    case SHORT -> "S";
                    case INT -> "I";
                });
            }

            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(boolean.class), methodName.toString());
            ex.addParameter(new CodeVariableElement(type(short.class), "instruction"));
            ex.addParameter(new CodeVariableElement(type(int.class), "stackEffect"));
            for (int i = 0; i < representativeInstruction.immediates.size(); i++) {
                ex.addParameter(new CodeVariableElement(representativeInstruction.immediates.get(i).kind().width.toType(context), "data" + i));
            }
            CodeTreeBuilder b = ex.createBuilder();

            b.startIf().string("stackEffect != 0").end().startBlock();
            b.statement("currentStackHeight += stackEffect");
            b.startAssert().string("currentStackHeight >= 0").end();
            b.end();

            b.startIf().string("stackEffect > 0").end().startBlock();
            b.statement("updateMaxStackHeight(currentStackHeight)");
            b.end();

            b.startIf().string("!reachable").end().startBlock();
            b.statement("return false");
            b.end();

            b.declaration(type(int.class), "newBci", "checkBci(bci + " + representativeInstruction.getInstructionLength() + ")");
            b.startIf().string("newBci > bc.length").end().startBlock();
            b.statement("ensureBytecodeCapacity(newBci)");
            b.end();

            b.end();

            b.statement(writeInstruction("bc", "bci + 0", "instruction"));
            for (int i = 0; i < representativeInstruction.immediates.size(); i++) {
                InstructionImmediate immediate = representativeInstruction.immediates.get(i);
                // Use a general immediate name instead of this particular immediate's name.
                InstructionImmediate representativeImmediate = new InstructionImmediate(immediate.offset(), immediate.kind(), Integer.toString(i));
                b.statement(writeImmediate("bc", "bci", "data" + i, representativeImmediate));
            }

            b.statement("bci = newBci");
            b.statement("return true");

            return ex;
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

        private CodeExecutableElement createUpdateMaxStackHeight() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "updateMaxStackHeight");
            ex.addParameter(new CodeVariableElement(type(int.class), "stackHeight"));
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("maxStackHeight = Math.max(maxStackHeight, stackHeight)");

            b.startIf().string("maxStackHeight > Short.MAX_VALUE").end().startBlock();
            emitThrowEncodingException(b, "\"Maximum stack height exceeded.\"");
            b.end();
            b.end(2);
            return ex;
        }

        private CodeExecutableElement createEnsureBytecodeCapacity() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "ensureBytecodeCapacity");
            ex.addParameter(new CodeVariableElement(type(int.class), "size"));
            CodeTreeBuilder b = ex.createBuilder();
            b.startIf().string("size > bc.length").end().startBlock();
            b.startAssign("bc").startStaticCall(type(Arrays.class), "copyOf");
            b.string("bc");
            b.string("Math.max(size, bc.length * 2)");
            b.end(2); // assign, static call
            b.end(); // if block
            return ex;
        }

        private CodeExecutableElement createDoEmitVariadic() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "doEmitVariadic");
            ex.addParameter(new CodeVariableElement(type(int.class), "count"));
            CodeTreeBuilder b = ex.createBuilder();

            b.statement("currentStackHeight -= count - 1");
            b.startIf().string("!reachable").end().startBlock();
            b.statement("return");
            b.end();

            int variadicCount = model.loadVariadicInstruction.length - 1;

            b.startIf().string("count <= ").string(variadicCount).end().startBlock();

            InstructionEncoding loadVariadicEncoding = model.loadVariadicInstruction[0].getInstructionEncoding();
            for (int i = 1; i < model.loadVariadicInstruction.length; i++) {
                if (!loadVariadicEncoding.equals(model.loadVariadicInstruction[i].getInstructionEncoding())) {
                    throw new AssertionError("load.variadic instruction did not match expected encoding.");
                }
            }

            b.startStatement().startCall(ensureDoEmitInstructionCreated(model.loadVariadicInstruction[0]).getSimpleName().toString());
            b.startCall("safeCastShort").startGroup().tree(createInstructionConstant(model.loadVariadicInstruction[0])).string(" + count").end(2);
            b.string("0");
            b.end(2);
            b.end().startElseBlock();

            b.statement("updateMaxStackHeight(currentStackHeight + count)");
            b.statement("int elementCount = count + 1");
            buildEmitInstruction(b, model.storeNullInstruction);

            b.startWhile().string("elementCount > 8").end().startBlock();
            buildEmitInstruction(b, model.loadVariadicInstruction[variadicCount]);
            b.statement("elementCount -= 7");
            b.end();

            b.startIf().string("elementCount > 0").end().startBlock();

            b.startStatement().startCall(ensureDoEmitInstructionCreated(model.loadVariadicInstruction[0]).getSimpleName().toString());
            b.startCall("safeCastShort").startGroup().tree(createInstructionConstant(model.loadVariadicInstruction[0])).string(" + elementCount").end(2);
            b.string("0");
            b.end(2);
            b.end();
            buildEmitInstruction(b, model.mergeVariadicInstruction);
            b.end();

            b.startIf().string("count == 0").end().startBlock();
            b.lineComment("pushed empty array");
            b.statement("updateMaxStackHeight(currentStackHeight)");
            b.end();

            return ex;
        }

        private void buildEmitInstruction(CodeTreeBuilder b, InstructionModel instr, String... arguments) {
            int stackEffect = switch (instr.kind) {
                case BRANCH, BRANCH_BACKWARD, //
                                TAG_ENTER, TAG_LEAVE, TAG_LEAVE_VOID, TAG_RESUME, TAG_YIELD, //
                                LOAD_LOCAL_MATERIALIZED, CLEAR_LOCAL, YIELD -> {
                    yield 0;
                }
                case STORE_NULL, LOAD_VARIADIC, MERGE_VARIADIC -> {
                    /*
                     * NB: These instructions *do* have stack effects. However, they are only used
                     * by doEmitVariadic, which does stack height computations itself. Use 0 so we
                     * don't update the stack height when emitting their instructions.
                     */
                    yield 0;
                }
                case DUP, LOAD_ARGUMENT, LOAD_CONSTANT, LOAD_NULL, LOAD_LOCAL, LOAD_EXCEPTION -> 1;
                case RETURN, THROW, BRANCH_FALSE, POP, STORE_LOCAL, MERGE_CONDITIONAL -> -1;
                case STORE_LOCAL_MATERIALIZED -> -2;
                case CUSTOM -> (instr.signature.isVoid ? 0 : 1) - instr.signature.dynamicOperandCount;
                case CUSTOM_SHORT_CIRCUIT -> {
                    /*
                     * NB: This code is a little confusing, because the stack height actually
                     * depends on whether the short circuit operation continues.
                     *
                     * What we track here is the stack height for the instruction immediately after
                     * this one (the one executed when we "continue" the short circuit operation).
                     * The code we generate carefully ensures that each path branching to the "end"
                     * leaves a single value on the stack.
                     */
                    ShortCircuitInstructionModel shortCircuitInstruction = instr.shortCircuitModel;
                    if (shortCircuitInstruction.duplicatesOperandOnStack()) {
                        // Consume the boolean value and pop the DUP'd original value.
                        yield -2;
                    } else {
                        // Consume the boolean value.
                        yield -1;
                    }
                }
                default -> throw new UnsupportedOperationException();
            };

            CodeExecutableElement doEmitInstruction = ensureDoEmitInstructionCreated(instr);
            b.startStatement().startCall(doEmitInstruction.getSimpleName().toString());
            b.tree(createInstructionConstant(instr));
            b.string(stackEffect);
            int argumentsLength = arguments != null ? arguments.length : 0;
            if (argumentsLength != instr.immediates.size()) {
                throw new AssertionError("Invalid number of immediates for instruction " + instr.name + ". Expected " + instr.immediates.size() + " but got " + argumentsLength + ". Immediates" +
                                instr.getImmediates());
            }

            if (arguments != null) {
                for (String argument : arguments) {
                    b.string(argument);
                }
            }
            b.end(2);
        }

        private CodeExecutableElement createDoEmitSourceInfo() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "doEmitSourceInfo");
            ex.addParameter(new CodeVariableElement(type(int.class), "sourceIndex"));
            ex.addParameter(new CodeVariableElement(type(int.class), "startBci"));
            ex.addParameter(new CodeVariableElement(type(int.class), "endBci"));
            ex.addParameter(new CodeVariableElement(type(int.class), "start"));
            ex.addParameter(new CodeVariableElement(type(int.class), "length"));

            CodeTreeBuilder b = ex.createBuilder();

            b.startAssert().string("parseSources").end();

            b.startIf().string("rootOperationSp == -1").end().startBlock();
            b.returnStatement();
            b.end();

            b.declaration(type(int.class), "index", "sourceInfoIndex");
            b.declaration(type(int.class), "prevIndex", "index - SOURCE_INFO_LENGTH");

            b.startIf();
            b.string("prevIndex >= 0").newLine().startIndention();
            b.string(" && (sourceInfo[prevIndex + SOURCE_INFO_OFFSET_SOURCE]) == sourceIndex").newLine();
            b.string(" && (sourceInfo[prevIndex + SOURCE_INFO_OFFSET_START]) == start").newLine();
            b.string(" && (sourceInfo[prevIndex + SOURCE_INFO_OFFSET_LENGTH]) == length");
            b.end(2).startBlock();

            b.startIf().string("(sourceInfo[prevIndex + SOURCE_INFO_OFFSET_START_BCI]) == startBci").newLine().startIndention();
            b.string(" && (sourceInfo[prevIndex + SOURCE_INFO_OFFSET_END_BCI]) == endBci");
            b.end(2).startBlock();
            b.lineComment("duplicate entry");
            b.statement("return");
            b.end();

            b.startElseIf().string("(sourceInfo[prevIndex + SOURCE_INFO_OFFSET_END_BCI]) == startBci").end().startBlock();
            b.lineComment("contiguous entry");
            b.statement("sourceInfo[prevIndex + SOURCE_INFO_OFFSET_END_BCI] = endBci");
            b.statement("return");
            b.end();

            b.end(); // if source, start, length match

            b.startIf().string("index >= sourceInfo.length").end().startBlock();
            b.statement("sourceInfo = Arrays.copyOf(sourceInfo, sourceInfo.length * 2)");
            b.end();

            b.statement("sourceInfo[index + SOURCE_INFO_OFFSET_START_BCI] = startBci");
            b.statement("sourceInfo[index + SOURCE_INFO_OFFSET_END_BCI] = endBci");
            b.statement("sourceInfo[index + SOURCE_INFO_OFFSET_SOURCE] = sourceIndex");
            b.statement("sourceInfo[index + SOURCE_INFO_OFFSET_START] = start");
            b.statement("sourceInfo[index + SOURCE_INFO_OFFSET_LENGTH] = length");

            b.statement("sourceInfoIndex = index + SOURCE_INFO_LENGTH");

            BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "SOURCE_INFO_OFFSET_START_BCI")).createInitBuilder().string("0");
            BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "SOURCE_INFO_OFFSET_END_BCI")).createInitBuilder().string("1");
            BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "SOURCE_INFO_OFFSET_SOURCE")).createInitBuilder().string("2");
            BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "SOURCE_INFO_OFFSET_START")).createInitBuilder().string("3");
            BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "SOURCE_INFO_OFFSET_LENGTH")).createInitBuilder().string("4");
            BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "SOURCE_INFO_LENGTH")).createInitBuilder().string("5");

            return ex;
        }

        private CodeExecutableElement createDoEmitFinallyHandler() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "doEmitFinallyHandler");
            ex.addParameter(new CodeVariableElement(dataClasses.get(model.tryFinallyOperation).asType(), "TryFinallyData"));
            ex.addParameter(new CodeVariableElement(type(int.class), "finallyOperationSp"));

            CodeTreeBuilder b = ex.createBuilder();

            b.startAssert().string("TryFinallyData.finallyHandlerSp == ", UNINIT).end();
            b.startTryBlock();
            b.statement("TryFinallyData.finallyHandlerSp = operationSp");
            buildBegin(b, model.finallyHandlerOperation, safeCastShort("finallyOperationSp"));
            b.statement("TryFinallyData.finallyGenerator.run()");
            buildEnd(b, model.finallyHandlerOperation);
            b.end().startFinallyBlock();
            b.statement("TryFinallyData.finallyHandlerSp = ", UNINIT);
            b.end();

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
            b.startIf().string("handlerTableSize > 0").end().startBlock();
            b.declaration(type(int.class), "previousEntry", "handlerTableSize - EXCEPTION_HANDLER_LENGTH");
            b.declaration(type(int.class), "previousEndBci", "handlerTable[previousEntry + EXCEPTION_HANDLER_OFFSET_END_BCI]");
            b.declaration(type(int.class), "previousKind", "handlerTable[previousEntry + EXCEPTION_HANDLER_OFFSET_KIND]");
            b.declaration(type(int.class), "previousHandlerBci", "handlerTable[previousEntry + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI]");
            b.startIf().string("previousEndBci == startBci && previousKind == handlerKind && previousHandlerBci == handlerBci").end().startBlock();
            b.statement("handlerTable[previousEntry + EXCEPTION_HANDLER_OFFSET_END_BCI] = endBci");
            b.startReturn().string(UNINIT).end();
            b.end(); // if same handler and contiguous
            b.end(); // if table non-empty

            b.startIf().string("handlerTable.length <= handlerTableSize + EXCEPTION_HANDLER_LENGTH").end().startBlock();
            b.statement("handlerTable = Arrays.copyOf(handlerTable, handlerTable.length * 2)");
            b.end();

            b.statement("int result = handlerTableSize");
            b.statement("handlerTable[handlerTableSize + EXCEPTION_HANDLER_OFFSET_START_BCI] = startBci");
            b.statement("handlerTable[handlerTableSize + EXCEPTION_HANDLER_OFFSET_END_BCI] = endBci");
            b.statement("handlerTable[handlerTableSize + EXCEPTION_HANDLER_OFFSET_KIND] = handlerKind");
            b.statement("handlerTable[handlerTableSize + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI] = handlerBci");
            b.statement("handlerTable[handlerTableSize + EXCEPTION_HANDLER_OFFSET_HANDLER_SP] = handlerSp");
            b.statement("handlerTableSize += EXCEPTION_HANDLER_LENGTH");

            b.statement("return result");

            BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "EXCEPTION_HANDLER_OFFSET_START_BCI")).createInitBuilder().string("0");
            BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "EXCEPTION_HANDLER_OFFSET_END_BCI")).createInitBuilder().string("1");
            BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "EXCEPTION_HANDLER_OFFSET_KIND")).createInitBuilder().string("2");
            BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "EXCEPTION_HANDLER_OFFSET_HANDLER_BCI")).createInitBuilder().string("3");
            BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "EXCEPTION_HANDLER_OFFSET_HANDLER_SP")).createInitBuilder().string("4");
            BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "EXCEPTION_HANDLER_LENGTH")).createInitBuilder().string("5");

            return ex;
        }

        private CodeExecutableElement createFailState() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(RuntimeException.class), "failState");
            ex.addParameter(new CodeVariableElement(type(String.class), "message"));
            CodeTreeBuilder b = ex.createBuilder();

            b.startThrow().startNew(type(IllegalStateException.class));
            b.startGroup();
            b.doubleQuote("Invalid builder usage: ");
            b.string(" + ").string("message").string(" + ").doubleQuote(" Operation stack: ").string(" + dumpAt()");
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
            b.startDeclaration(type(StringBuilder.class), "b").startNew(type(StringBuilder.class)).end().end();
            // for operation stacks
            b.startFor().string("int i = 0; i < operationSp; i++").end().startBlock();
            b.startStatement().startCall("b.append").doubleQuote("(").end().end();
            b.startStatement().startCall("b.append").string("operationStack[i].toString(this)").end().end();
            b.end(); // for

            b.startFor().string("int i = 0; i < operationSp; i++").end().startBlock();
            b.startStatement().startCall("b.append").doubleQuote(")").end().end();
            b.end(); // for

            b.statement("return b.toString()");
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

            b.startStatement().startCall("b.append").doubleQuote("at=").end().end();

            // for operation stacks
            b.startFor().string("int i = 0; i < operationSp; i++").end().startBlock();
            b.startStatement().startCall("b.append").doubleQuote("(").end().end();
            b.startStatement().startCall("b.append").string("operationStack[i].toString(this)").end().end();
            b.end(); // for

            b.startFor().string("int i = 0; i < operationSp; i++").end().startBlock();
            b.startStatement().startCall("b.append").doubleQuote(")").end().end();
            b.end(); // for

            b.startStatement().startCall("b.append").doubleQuote(", mode=").end().end();

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

            b.startStatement().startCall("b.append").doubleQuote(", bytecodeIndex=").end().startCall(".append").string("bci").end().end();
            b.startStatement().startCall("b.append").doubleQuote(", stackPointer=").end().startCall(".append").string("currentStackHeight").end().end();
            b.startStatement().startCall("b.append").doubleQuote(", bytecodes=").end().startCall(".append").string("parseBytecodes").end().end();
            b.startStatement().startCall("b.append").doubleQuote(", sources=").end().startCall(".append").string("parseSources").end().end();

            if (!model.instrumentations.isEmpty()) {
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

            b.startStatement().startCall("b.append").doubleQuote("]").end().end();

            b.statement("return b.toString()");
            return ex;
        }

        private CodeExecutableElement createDoEmitRoot() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "doEmitRoot");
            CodeTreeBuilder b = ex.createBuilder();

            b.startIf().string("!parseSources").end().startBlock();
            b.lineComment("Nothing to do here without sources");
            b.statement("return");
            b.end();

            /**
             * Walk the entire operation stack (past any root operations) and find enclosing source
             * sections. The entire root node's bytecode range is covered by the source section.
             */
            buildOperationStackWalk(b, "0", () -> {
                b.startSwitch().string("operationStack[i].operation").end().startBlock();

                b.startCase().tree(createOperationConstant(model.sourceSectionOperation)).end();
                b.startCaseBlock();
                emitCastOperationData(b, model.sourceSectionOperation, "i");
                b.startStatement().startCall("doEmitSourceInfo");
                b.string("operationData.sourceIndex");
                b.string("0");
                b.string("bci");
                b.string("operationData.start");
                b.string("operationData.length");
                b.end(2);

                b.statement("break");
                b.end(); // case epilog

                b.end(); // switch
            });

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
            emitStackWalksBeforeEarlyExit(ex, OperationKind.RETURN, "return", "rootOperationSp + 1");
            return ex;
        }

        /**
         * Before emitting a yield, we may need to emit additional instructions for tag
         * instrumentation.
         */
        private CodeExecutableElement createDoEmitTagYield() {
            if (!model.enableTagInstrumentation || !model.enableYield) {
                throw new AssertionError("cannot produce method");
            }

            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "doEmitTagYield");

            CodeTreeBuilder b = ex.createBuilder();
            b.startIf().string("tags == 0").end().startBlock();
            b.returnDefault();
            b.end();

            buildOperationStackWalk(b, () -> {
                b.startSwitch().string("operationStack[i].operation").end().startBlock();

                OperationModel op = model.findOperation(OperationKind.TAG);
                b.startCase().tree(createOperationConstant(op)).end();
                b.startBlock();
                emitCastOperationData(b, op, "i");
                buildEmitInstruction(b, model.tagYieldInstruction, "operationData.nodeId");
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
            if (!model.enableTagInstrumentation || !model.enableYield) {
                throw new AssertionError("cannot produce method");
            }

            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "doEmitTagResume");
            CodeTreeBuilder b = ex.createBuilder();

            b.startIf().string("tags == 0").end().startBlock();
            b.returnDefault();
            b.end();

            buildOperationStackWalkFromBottom(b, "rootOperationSp", () -> {
                b.startSwitch().string("operationStack[i].operation").end().startBlock();
                OperationModel op = model.findOperation(OperationKind.TAG);
                b.startCase().tree(createOperationConstant(op)).end();
                b.startBlock();
                emitCastOperationData(b, op, "i");
                buildEmitInstruction(b, model.tagResumeInstruction, "operationData.nodeId");
                b.statement("break");
                b.end(); // case tag

                b.end(); // switch
            });

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

            b.startIf().string("handlerTable[i + EXCEPTION_HANDLER_OFFSET_KIND] != HANDLER_CUSTOM").end().startBlock();
            b.statement("continue");
            b.end();
            b.startIf().string("handlerTable[i + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI] != -handlerId").end().startBlock();
            b.statement("continue");
            b.end();

            b.statement("handlerTable[i + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI] = handlerBci");
            b.statement("handlerTable[i + EXCEPTION_HANDLER_OFFSET_HANDLER_SP] = handlerSp");
            b.end();

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
                b.startSwitch().string("operationStack[i].operation").end().startBlock();

                if (model.enableTagInstrumentation) {
                    b.startCase().tree(createOperationConstant(model.tagOperation)).end();
                    b.startBlock();
                    emitCastOperationData(b, model.tagOperation, "i");
                    b.startIf().string("reachable").end().startBlock();
                    if (operationKind == OperationKind.RETURN) {
                        buildEmitInstruction(b, model.tagLeaveValueInstruction, buildTagLeaveArguments(model.tagLeaveValueInstruction));
                        b.statement("childBci = bci - " + model.tagLeaveValueInstruction.getInstructionLength());
                    } else {
                        if (operationKind != OperationKind.BRANCH) {
                            throw new AssertionError("unexpected operation kind used for unwind code generation.");
                        }
                        buildEmitInstruction(b, model.tagLeaveVoidInstruction, "operationData.nodeId");
                    }
                    b.statement("doCreateExceptionHandler(operationData.handlerStartBci, bci, HANDLER_TAG_EXCEPTIONAL, operationData.nodeId, operationData.startStackHeight)");
                    b.statement("needsRewind = true");
                    b.end(); // reachable
                    b.statement("break");
                    b.end(); // case tag
                }

                if (operationKind == OperationKind.RETURN && model.epilogReturn != null) {
                    b.startCase().tree(createOperationConstant(model.epilogReturn.operation)).end();
                    b.startBlock();
                    buildEmitOperationInstruction(b, model.epilogReturn.operation, "childBci", "i", null);
                    b.statement("childBci = bci - " + model.epilogReturn.operation.instruction.getInstructionLength());
                    b.statement("break");
                    b.end(); // case epilog
                }

                for (OperationKind finallyOpKind : List.of(OperationKind.TRY_FINALLY, OperationKind.TRY_CATCH_OTHERWISE)) {
                    b.startCase().tree(createOperationConstant(model.findOperation(finallyOpKind))).end();
                    b.startBlock();
                    emitCastOperationData(b, model.tryFinallyOperation, "i");
                    b.startIf().string("operationStack[i].childCount == 0 /* still in try */").end().startBlock();
                    b.startIf().string("reachable").end().startBlock();
                    emitExtraExceptionTableEntry(b);
                    b.statement("needsRewind = true");
                    b.end(); // if reachable
                    b.statement("doEmitFinallyHandler(operationData, i)");
                    b.end(); // if in try
                    b.statement("break");
                    b.end(); // case finally
                }

                b.startCase().tree(createOperationConstant(model.findOperation(OperationKind.TRY_CATCH))).end();
                b.startBlock();
                emitCastOperationData(b, model.tryCatchOperation, "i");
                b.startIf().string("operationStack[i].childCount == 0 /* still in try */ && reachable").end().startBlock();
                emitExtraExceptionTableEntry(b);
                b.statement("needsRewind = true");
                b.end(); // if in try and reachable
                b.statement("break");
                b.end(); // case trycatch

                b.startCase().tree(createOperationConstant(model.sourceSectionOperation)).end();
                b.startBlock();
                emitCastOperationData(b, model.sourceSectionOperation, "i");
                b.startStatement().startCall("doEmitSourceInfo");
                b.string("operationData.sourceIndex");
                b.string("operationData.startBci");
                b.string("bci");
                b.string("operationData.start");
                b.string("operationData.length");
                b.end(2);
                b.statement("needsRewind = true");
                b.statement("break");
                b.end(); // case source section

                if (model.enableBlockScoping) {
                    b.startCase().tree(createOperationConstant(model.blockOperation)).end();
                    b.startBlock();
                    emitCastOperationData(b, model.blockOperation, "i");
                    b.startFor().string("int j = 0; j < operationData.numLocals; j++").end().startBlock();
                    b.statement("locals[operationData.locals[j] + LOCALS_OFFSET_END_BCI] = bci");
                    if (operationKind == OperationKind.BRANCH) {
                        buildEmitInstruction(b, model.clearLocalInstruction, safeCastShort("locals[operationData.locals[j] + LOCALS_OFFSET_FRAME_INDEX]"));
                    }
                    b.statement("needsRewind = true");
                    b.end(); // for
                    b.statement("break");
                    b.end(); // case block
                }

                b.end(); // switch
            });
        }

        private void emitExtraExceptionTableEntry(CodeTreeBuilder b) {
            b.startDeclaration(type(int.class), "handlerTableIndex");
            b.string("doCreateExceptionHandler(operationData.tryStartBci, bci, HANDLER_CUSTOM, -operationData.handlerId, ", UNINIT, " /* stack height */)");
            b.end();
            b.startIf().string("handlerTableIndex != ", UNINIT).end().startBlock();
            b.startIf().string("operationData.extraTableEntriesStart == ", UNINIT).end().startBlock();
            b.statement("operationData.extraTableEntriesStart = handlerTableIndex");
            b.end();
            b.statement("operationData.extraTableEntriesEnd = handlerTableIndex + EXCEPTION_HANDLER_LENGTH");
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
                b.startSwitch().string("operationStack[i].operation").end().startBlock();

                if (model.enableTagInstrumentation) {
                    b.startCase().tree(createOperationConstant(model.tagOperation)).end();
                    b.startBlock();
                    emitCastOperationData(b, model.tagOperation, "i");
                    b.statement("operationData.handlerStartBci = bci");
                    b.statement("break");
                    b.end();
                }

                b.startCase().tree(createOperationConstant(model.findOperation(OperationKind.TRY_FINALLY))).end();
                b.startCase().tree(createOperationConstant(model.findOperation(OperationKind.TRY_CATCH_OTHERWISE))).end();
                b.startCaseBlock();
                b.startIf().string("operationStack[i].childCount == 0 /* still in try */").end().startBlock();
                emitCastOperationData(b, model.tryFinallyOperation, "i");
                b.statement("operationData.tryStartBci = bci");
                b.end(); // if
                b.statement("break");
                b.end(); // case finally

                b.startCase().tree(createOperationConstant(model.findOperation(OperationKind.TRY_CATCH))).end();
                b.startCaseBlock();
                b.startIf().string("operationStack[i].childCount == 0 /* still in try */").end().startBlock();
                emitCastOperationData(b, model.tryCatchOperation, "i");
                b.statement("operationData.tryStartBci = bci");
                b.end(); // if
                b.statement("break");
                b.end(); // case trycatch

                b.startCase().tree(createOperationConstant(model.sourceSectionOperation)).end();
                b.startBlock();
                emitCastOperationData(b, model.sourceSectionOperation, "i");
                b.statement("operationData.startBci = bci");
                b.statement("break");
                b.end(); // case source section

                if (model.enableBlockScoping) {
                    b.startCase().tree(createOperationConstant(model.blockOperation)).end();
                    b.startBlock();
                    emitCastOperationData(b, model.blockOperation, "i");
                    b.startFor().string("int j = 0; j < operationData.numLocals; j++").end().startBlock();
                    b.declaration(type(int.class), "prevTableIndex", "operationData.locals[j]");

                    /**
                     * We need to emit multiple local ranges if instructions were emitted after
                     * unwinding the block (i.e., instructions at which the local is not live).
                     * Otherwise, we can reuse the same local table entry. We cannot reuse the entry
                     * after a branch because we emit a clear.local instruction when unwinding.
                     */
                    if (operationKind != OperationKind.BRANCH) {
                        b.declaration(type(int.class), "endBci", "locals[prevTableIndex + LOCALS_OFFSET_END_BCI]");
                        b.startIf().string("endBci == bci").end().startBlock();
                        b.lineComment("No need to split. Reuse the existing entry.");
                        b.statement("locals[prevTableIndex + LOCALS_OFFSET_END_BCI] = ", UNINIT);
                        b.statement("continue");
                        b.end();
                    }

                    b.lineComment("Create a new table entry with a new bytecode range and the same metadata.");
                    b.declaration(type(int.class), "localIndex", "locals[prevTableIndex + LOCALS_OFFSET_LOCAL_INDEX]");
                    b.declaration(type(int.class), "frameIndex", "locals[prevTableIndex + LOCALS_OFFSET_FRAME_INDEX]");
                    b.declaration(type(int.class), "nameIndex", "locals[prevTableIndex + LOCALS_OFFSET_NAME]");
                    b.declaration(type(int.class), "infoIndex", "locals[prevTableIndex + LOCALS_OFFSET_INFO]");
                    b.statement("operationData.locals[j] = doEmitLocal(localIndex, frameIndex, nameIndex, infoIndex)");
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
                args = new String[]{"operationData.nodeId"};
            } else {
                args = new String[]{"operationData.nodeId", "childBci"};
            }
            return args;
        }

        private CodeExecutableElement createAllocateNode() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(int.class), "allocateNode");
            CodeTreeBuilder b = ex.createBuilder();
            b.startIf().string("!reachable").end().startBlock();
            b.statement("return -1");
            b.end();

            b.startReturn().startCall("checkOverflowInt");
            b.string("numNodes++");
            b.doubleQuote("Node counter");
            b.end(2);

            return ex;
        }

        private CodeExecutableElement createAllocateBytecodeLocal() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(short.class), "allocateBytecodeLocal");
            CodeTreeBuilder b = ex.createBuilder();

            b.startReturn().startCall("checkOverflowShort");
            b.string("(short) numLocals++");
            b.doubleQuote("Number of locals");
            b.end(2);

            return ex;
        }

        private CodeExecutableElement createAllocateBranchProfile() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(int.class), "allocateBranchProfile");
            CodeTreeBuilder b = ex.createBuilder();

            b.startIf().string("!reachable").end().startBlock();
            b.statement("return -1");
            b.end();

            b.startReturn().startCall("checkOverflowInt");
            b.string("numConditionalBranches++");
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
            b.string("constantPool.allocateSlot()");
            b.end();

            return ex;
        }

        static final class SavedStateElement extends CodeTypeElement {

            SavedStateElement() {
                super(Set.of(PRIVATE, STATIC), ElementKind.CLASS, null, "SavedState");
            }

            void lazyInit(List<CodeVariableElement> builderState) {
                this.addAll(builderState);
                this.add(createConstructorUsingFields(Set.of(), this, null));
            }
        }

        class OperationDataClassesFactory {

            private Collection<CodeTypeElement> create() {
                List<CodeTypeElement> result = new ArrayList<>();

                if (model.enableBlockScoping) {
                    scopeDataType = new ScopeDataElement();
                    result.add(scopeDataType);
                }

                Map<String, CodeTypeElement> dataClassNames = new LinkedHashMap<>();
                for (OperationModel operation : model.getOperations()) {
                    CodeTypeElement type = dataClasses.get(operation);
                    if (type == null) {
                        type = createDataClass(operation);
                        if (type != null) {
                            String name = type.getSimpleName().toString();
                            CodeTypeElement typeSameName = dataClassNames.get(name);
                            if (typeSameName == null) {
                                dataClassNames.put(name, type);
                            } else {
                                type = typeSameName;
                            }
                        }
                    }
                    dataClasses.put(operation, type);
                }
                result.addAll(dataClassNames.values());
                return result;
            }

            private CodeTypeElement createDataClass(OperationModel operation) {
                String name = null; // default name
                TypeMirror superType = null; // default type
                List<CodeExecutableElement> methods = List.of();
                List<DataClassField> fields;
                switch (operation.kind) {
                    case ROOT:
                        name = "RootData";
                        fields = new ArrayList<>(5);
                        fields.addAll(List.of(//
                                        field(type(short.class), "index").asFinal(),
                                        field(type(boolean.class), "producedValue").withInitializer("false"),
                                        field(type(int.class), "childBci").withInitializer(UNINIT),
                                        field(type(boolean.class), "reachable").withInitializer("true")));
                        if (model.prolog != null && model.prolog.operation.operationEndArguments.length != 0) {
                            fields.add(field(type(int.class), "prologBci").withInitializer(UNINIT));
                        }
                        if (model.enableBlockScoping) {
                            superType = scopeDataType.asType();
                        }
                        break;
                    case BLOCK:
                        name = "BlockData";
                        fields = List.of(//
                                        field(type(int.class), "startStackHeight").asFinal(),
                                        field(type(boolean.class), "producedValue").withInitializer("false"),
                                        field(type(int.class), "childBci").withInitializer(UNINIT));
                        if (model.enableBlockScoping) {
                            superType = scopeDataType.asType();
                        }
                        break;
                    case TAG:
                        name = "TagOperationData";
                        fields = List.of(//
                                        field(type(int.class), "nodeId").asFinal(),
                                        field(type(boolean.class), "operationReachable").asFinal(),
                                        field(type(int.class), "startStackHeight").asFinal(),
                                        field(tagNode.asType(), "node").asFinal(),
                                        field(type(int.class), "handlerStartBci").withInitializer("node.enterBci"),
                                        field(type(boolean.class), "producedValue").withInitializer("false"),
                                        field(type(int.class), "childBci").withInitializer(UNINIT),
                                        field(generic(type(List.class), tagNode.asType()), "children").withInitializer("null"));

                        break;
                    case SOURCE_SECTION:
                        name = "SourceSectionData";
                        fields = List.of(//
                                        field(type(int.class), "sourceIndex").asFinal(),
                                        field(type(int.class), "startBci"),
                                        field(type(int.class), "start").asFinal(),
                                        field(type(int.class), "length").asFinal(),
                                        field(type(boolean.class), "producedValue").withInitializer("false"),
                                        field(type(int.class), "childBci").withInitializer(UNINIT));
                        break;
                    case SOURCE:
                        name = "SourceData";
                        fields = List.of(//
                                        field(type(int.class), "sourceIndex").asFinal(),
                                        field(type(boolean.class), "producedValue").withInitializer("false"),
                                        field(type(int.class), "childBci").withInitializer(UNINIT));
                        break;
                    case RETURN:
                        name = "ReturnOperationData";
                        fields = List.of(//
                                        field(type(boolean.class), "producedValue").withInitializer("false"),
                                        field(type(int.class), "childBci").withInitializer(UNINIT));
                        break;
                    case STORE_LOCAL:
                    case STORE_LOCAL_MATERIALIZED:
                        if (model.usesBoxingElimination()) {
                            name = "StoreLocalData";
                            fields = List.of(//
                                            field(bytecodeLocalImpl.asType(), "local"),
                                            field(type(int.class), "childBci").withInitializer(UNINIT));
                        } else {
                            name = null;
                            fields = List.of();
                        }
                        break;
                    case IF_THEN:
                        name = "IfThenData";
                        fields = List.of(//
                                        field(type(boolean.class), "thenReachable"),
                                        field(type(int.class), "falseBranchFixupBci").withInitializer(UNINIT));
                        break;
                    case IF_THEN_ELSE:
                        name = "IfThenElseData";
                        fields = List.of(//
                                        field(type(boolean.class), "thenReachable"),
                                        field(type(boolean.class), "elseReachable"),
                                        field(type(int.class), "falseBranchFixupBci").withInitializer(UNINIT),
                                        field(type(int.class), "endBranchFixupBci").withInitializer(UNINIT));
                        break;
                    case CONDITIONAL:
                        name = "ConditionalData";
                        if (model.usesBoxingElimination()) {
                            fields = List.of(//
                                            field(type(boolean.class), "thenReachable"),
                                            field(type(boolean.class), "elseReachable"),
                                            field(type(int.class), "falseBranchFixupBci").withInitializer(UNINIT),
                                            field(type(int.class), "endBranchFixupBci").withInitializer(UNINIT),
                                            field(type(int.class), "child0Bci").withInitializer(UNINIT),
                                            field(type(int.class), "child1Bci").withInitializer(UNINIT));
                        } else {
                            fields = List.of(//
                                            field(type(boolean.class), "thenReachable"),
                                            field(type(boolean.class), "elseReachable"),
                                            field(type(int.class), "falseBranchFixupBci").withInitializer(UNINIT),
                                            field(type(int.class), "endBranchFixupBci").withInitializer(UNINIT));
                        }
                        break;
                    case WHILE:
                        name = "WhileData";
                        fields = List.of(//
                                        field(type(int.class), "whileStartBci").asFinal(),
                                        field(type(boolean.class), "bodyReachable"),
                                        field(type(int.class), "endBranchFixupBci").withInitializer(UNINIT));
                        break;
                    case TRY_CATCH:
                        name = "TryCatchData";
                        fields = List.of(//
                                        field(type(int.class), "handlerId").asFinal(),
                                        field(type(short.class), "stackHeight").asFinal(),
                                        field(type(int.class), "tryStartBci"),
                                        field(type(boolean.class), "operationReachable").asFinal(),
                                        field(type(boolean.class), "tryReachable"),
                                        field(type(boolean.class), "catchReachable"),
                                        field(type(int.class), "endBranchFixupBci").withInitializer(UNINIT),
                                        field(type(int.class), "extraTableEntriesStart").withInitializer(UNINIT),
                                        field(type(int.class), "extraTableEntriesEnd").withInitializer(UNINIT));
                        break;
                    case TRY_FINALLY, TRY_CATCH_OTHERWISE:
                        name = "TryFinallyData";
                        fields = List.of(//
                                        field(type(int.class), "handlerId").asFinal(),
                                        field(type(short.class), "stackHeight").asFinal(),
                                        field(context.getDeclaredType(Runnable.class), "finallyGenerator").asFinal(),
                                        field(type(int.class), "tryStartBci"),
                                        field(type(boolean.class), "operationReachable").asFinal(),
                                        field(type(boolean.class), "tryReachable"),
                                        field(type(boolean.class), "catchReachable"),
                                        field(type(int.class), "endBranchFixupBci").withInitializer(UNINIT),
                                        field(type(int.class), "extraTableEntriesStart").withInitializer(UNINIT),
                                        field(type(int.class), "extraTableEntriesEnd").withInitializer(UNINIT),
                                        field(type(int.class), "finallyHandlerSp").withInitializer(UNINIT).withDoc(
                                                        """
                                                                        The index of the finally handler operation on the operation stack.
                                                                        This value is uninitialized unless a finally handler is being emitted, and allows us to
                                                                        walk the operation stack from bottom to top.
                                                                        """));
                        break;
                    case FINALLY_HANDLER:
                        name = "FinallyHandlerData";
                        fields = List.of(field(type(int.class), "finallyOperationSp").asFinal().withDoc(
                                        """
                                                        The index of the finally operation (TryFinally/TryCatchOtherwise) on the operation stack.
                                                        This index should only be used to skip over the handler when walking the operation stack.
                                                        It should *not* be used to access the finally operation data, because a FinallyHandler is
                                                        sometimes emitted after the finally operation has already been popped.
                                                        """));
                        break;
                    case CUSTOM, CUSTOM_INSTRUMENTATION:
                        if (operation.isTransparent()) {
                            name = "TransparentData";
                            fields = List.of(//
                                            field(type(boolean.class), "producedValue").withInitializer("false"),
                                            field(type(int.class), "childBci").withInitializer(UNINIT));
                        } else {
                            name = "CustomOperationData";
                            fields = List.of(//
                                            field(arrayOf(type(int.class)), "childBcis").asFinal(),
                                            field(arrayOf(type(int.class)), "constants").asFinal(),
                                            field(arrayOf(context.getDeclaredType(Object.class)), "locals").asFinal().asVarArgs());
                        }
                        break;
                    case CUSTOM_SHORT_CIRCUIT:
                        name = "CustomShortCircuitOperationData";
                        fields = List.of(//
                                        field(type(int.class), "childBci").withInitializer(UNINIT),
                                        field(type(int.class), "shortCircuitBci").withInitializer(UNINIT),
                                        field(generic(List.class, Integer.class), "branchFixupBcis").withInitializer("new ArrayList<>(4)"));
                        break;
                    default:
                        if (operation.isTransparent()) {
                            name = "TransparentData";
                            fields = List.of(//
                                            field(type(boolean.class), "producedValue"),
                                            field(type(int.class), "childBci"));
                        } else {
                            name = null;
                            fields = List.of();
                        }
                        break;
                }
                if (name == null) {
                    return null;
                } else {
                    CodeTypeElement result = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, name);
                    if (superType != null) {
                        result.setSuperClass(superType);
                    }

                    result.getEnclosedElements().addAll(methods);

                    Set<String> ignoreFields = new HashSet<>();
                    boolean isVarArgs = false;
                    for (DataClassField field : fields) {
                        if (field.initializer != null) {
                            ignoreFields.add(field.name);
                        }
                        isVarArgs = isVarArgs || field.isVarArgs;

                        result.add(field.toCodeVariableElement());
                    }
                    CodeExecutableElement ctor = createConstructorUsingFields(Set.of(), result, null, ignoreFields);

                    // Append custom initializers.
                    CodeTreeBuilder b = ctor.appendBuilder();
                    for (DataClassField field : fields) {
                        if (field.initializer != null) {
                            b.startAssign("this." + field.name);
                            b.string(field.initializer);
                            b.end();
                        }
                    }
                    ctor.setVarArgs(isVarArgs);

                    result.add(ctor);

                    return result;
                }

            }

            private DataClassField field(TypeMirror type, String name) {
                return new DataClassField(type, name);
            }

            private CodeExecutableElement createRegisterLocal() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), type(void.class), "registerLocal");
                ex.addParameter(new CodeVariableElement(type(int.class), "tableIndex"));

                CodeTreeBuilder b = ex.createBuilder();

                b.declaration(type(int.class), "localTableIndex", "numLocals++");
                b.startIf().string("locals == null").end().startBlock();
                b.startAssign("locals").startNewArray(arrayOf(type(int.class)), CodeTreeBuilder.singleString("8")).end().end();
                b.end();
                b.startElseIf().string("localTableIndex >= locals.length").end().startBlock();
                b.startAssign("locals").startStaticCall(type(Arrays.class), "copyOf");
                b.string("locals");
                b.string("locals.length * 2");
                b.end(2); // assign, static call
                b.end(); // if block
                b.statement("locals[localTableIndex] = tableIndex");

                return ex;
            }

            final class ScopeDataElement extends CodeTypeElement {

                ScopeDataElement() {
                    super(Set.of(PRIVATE, STATIC, ABSTRACT), ElementKind.CLASS, null, "ScopeData");
                    this.add(new CodeVariableElement(Set.of(), type(int.class), "frameOffset"));

                    CodeVariableElement locals = new CodeVariableElement(Set.of(), type(int[].class), "locals");
                    locals.createInitBuilder().string("null");
                    addJavadoc(locals, "The indices of this scope's locals in the global locals table. Used to patch in the end bci at the end of the scope.");
                    this.add(locals);

                    CodeVariableElement numLocals = new CodeVariableElement(Set.of(), type(int.class), "numLocals");
                    numLocals.createInitBuilder().string("0");
                    addJavadoc(numLocals, "The number of locals allocated in the frame for this scope.");
                    this.add(numLocals);

                    this.add(new CodeVariableElement(Set.of(), type(boolean.class), "valid")).createInitBuilder().string("true");

                    this.add(createRegisterLocal());
                }
            }

            private static final class DataClassField {
                final TypeMirror type;
                final String name;
                boolean isFinal;
                boolean isVarArgs;
                // If initializer is null, the field value is required as a constructor parameter
                String initializer;
                String doc;

                DataClassField(TypeMirror type, String name) {
                    this.type = type;
                    this.name = name;
                }

                DataClassField asFinal() {
                    this.isFinal = true;
                    return this;
                }

                DataClassField asVarArgs() {
                    this.isVarArgs = true;
                    return this;
                }

                DataClassField withInitializer(String newInitializer) {
                    this.initializer = newInitializer;
                    return this;
                }

                DataClassField withDoc(String newDoc) {
                    this.doc = newDoc;
                    return this;
                }

                CodeVariableElement toCodeVariableElement() {
                    Set<Modifier> mods = isFinal ? Set.of(FINAL) : Set.of();
                    CodeVariableElement result = new CodeVariableElement(mods, type, name);
                    if (doc != null) {
                        addJavadoc(result, doc);
                    }
                    return result;
                }
            }
        }

        final class OperationStackEntryElement extends CodeTypeElement {

            OperationStackEntryElement() {
                super(Set.of(PRIVATE, STATIC), ElementKind.CLASS, null, "OperationStackEntry");
            }

            private void lazyInit() {
                this.addAll(List.of(
                                new CodeVariableElement(Set.of(PRIVATE, FINAL), type(int.class), "operation"),
                                new CodeVariableElement(Set.of(PRIVATE, FINAL), type(Object.class), "data"),
                                new CodeVariableElement(Set.of(PRIVATE, FINAL), type(int.class), "sequenceNumber")));

                CodeVariableElement childCount = new CodeVariableElement(Set.of(PRIVATE), type(int.class), "childCount");
                childCount.createInitBuilder().string("0").end();
                CodeVariableElement declaredLabels = new CodeVariableElement(Set.of(PRIVATE), generic(context.getDeclaredType(ArrayList.class), types.BytecodeLabel), "declaredLabels");
                declaredLabels.createInitBuilder().string("null").end();
                this.add(childCount);
                this.add(declaredLabels);

                this.add(createConstructorUsingFields(Set.of(), this, null, Set.of("childCount", "declaredLabels")));
                this.add(createAddDeclaredLabel());
                this.add(createToString0());
                this.add(createToString1());
            }

            private CodeExecutableElement createAddDeclaredLabel() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), type(void.class), "addDeclaredLabel");
                ex.addParameter(new CodeVariableElement(types.BytecodeLabel, "label"));

                CodeTreeBuilder b = ex.createBuilder();

                b.startIf().string("declaredLabels == null").end().startBlock();
                b.statement("declaredLabels = new ArrayList<>(8)");
                b.end();

                b.statement("declaredLabels.add(label)");

                return ex;
            }

            private CodeExecutableElement createToString0() {
                CodeExecutableElement ex = GeneratorUtils.override(context.getDeclaredType(Object.class), "toString");
                CodeTreeBuilder b = ex.createBuilder();
                b.statement("return \"(\" + toString(null) + \")\"");
                return ex;
            }

            private CodeExecutableElement createToString1() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(String.class), "toString");
                ex.addParameter(new CodeVariableElement(bytecodeBuilderType, "builder"));
                CodeTreeBuilder b = ex.createBuilder();

                b.startDeclaration(type(StringBuilder.class), "b").startNew(type(StringBuilder.class)).end().end();
                b.startStatement().startCall("b.append").string("OPERATION_NAMES[operation]").end().end();

                b.startSwitch().string("operation").end().startBlock();
                for (OperationModel op : model.getOperations()) {
                    switch (op.kind) {
                        case STORE_LOCAL:
                        case STORE_LOCAL_MATERIALIZED:
                            b.startCase().tree(createOperationConstant(op)).end().startBlock();
                            b.startStatement().startCall("b.append").doubleQuote(" ").end().end();

                            b.declaration(getDataClassName(op), "operationData", "(" + getDataClassName(op) + ") data");
                            if (model.usesBoxingElimination()) {
                                b.startStatement().startCall("b.append").string("operationData.local.frameIndex").end().end();
                            } else {
                                b.startStatement().startCall("b.append").string("operationData.frameIndex").end().end();
                            }
                            b.end();
                            b.statement("break");
                            break;
                        case SOURCE:
                            b.startCase().tree(createOperationConstant(op)).end().startBlock();
                            b.startStatement().startCall("b.append").doubleQuote(" ").end().end();

                            b.declaration(getDataClassName(op), "operationData", "(" + getDataClassName(op) + ") data");
                            b.startStatement().startCall("b.append").string("operationData.sourceIndex").end().end();
                            b.startIf().string("builder != null").end().startBlock();
                            b.startStatement().startCall("b.append").doubleQuote(":").end().end();
                            b.startStatement().startCall("b.append").string("builder.sources.get(operationData.sourceIndex).getName()").end().end();
                            b.end();
                            b.end(); // case block
                            b.statement("break");
                            break;
                        case SOURCE_SECTION:
                            b.startCase().tree(createOperationConstant(op)).end().startBlock();
                            b.startStatement().startCall("b.append").doubleQuote(" ").end().end();
                            b.declaration(getDataClassName(op), "operationData", "(" + getDataClassName(op) + ") data");
                            b.startStatement().startCall("b.append").string("operationData.start").end().end();
                            b.startStatement().startCall("b.append").doubleQuote(":").end().end();
                            b.startStatement().startCall("b.append").string("operationData.length").end().end();
                            b.end();
                            b.statement("break");
                            break;
                        case TAG:
                            b.startCase().tree(createOperationConstant(op)).end().startBlock();
                            b.startStatement().startCall("b.append").doubleQuote(" ").end().end();
                            b.declaration(getDataClassName(op), "operationData", "(" + getDataClassName(op) + ") data");
                            b.startStatement().startCall("b.append").string("operationData.node").end().end();
                            b.end();
                            b.statement("break");
                            break;
                        case BLOCK:
                        case ROOT:
                            if (model.enableBlockScoping) {
                                b.startCase().tree(createOperationConstant(op)).end().startBlock();
                                b.declaration(getDataClassName(op), "operationData", "(" + getDataClassName(op) + ") data");

                                b.startIf().string("operationData.numLocals > 0").end().startBlock();
                                b.startStatement().startCall("b.append").doubleQuote(" locals=").end().end();
                                b.startStatement().startCall("b.append").string("operationData.numLocals").end().end();
                                b.end();
                                b.end();
                                b.statement("break");
                            }
                            break;
                    }
                }
                b.end(); // switch

                b.statement("return b.toString()");
                return ex;
            }
        }

        final class ConstantPoolElement extends CodeTypeElement {

            ConstantPoolElement() {
                super(Set.of(PRIVATE, STATIC), ElementKind.CLASS, null, "ConstantPool");
                List<CodeVariableElement> fields = List.of(
                                new CodeVariableElement(Set.of(PRIVATE, FINAL), generic(ArrayList.class, type(Object.class)), "constants"),
                                new CodeVariableElement(Set.of(PRIVATE, FINAL), generic(HashMap.class, type(Object.class), context.getDeclaredType(Integer.class)), "map"));

                this.addAll(fields);

                CodeExecutableElement ctor = createConstructorUsingFields(Set.of(), this, null, Set.of("constants", "map"));
                CodeTreeBuilder b = ctor.appendBuilder();
                b.statement("constants = new ArrayList<>()");
                b.statement("map = new HashMap<>()");
                this.add(ctor);

                this.add(createAddConstant());
                this.add(createAllocateSlot());
                this.add(createToArray());
            }

            private CodeExecutableElement createAddConstant() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(int.class),
                                "addConstant");
                ex.addParameter(new CodeVariableElement(type(Object.class), "constant"));

                CodeTreeBuilder b = ex.createBuilder();

                b.startIf().string("map.containsKey(constant)").end().startBlock();
                b.startReturn().string("map.get(constant)").end();
                b.end();

                b.statement("int index = constants.size()");
                b.statement("constants.add(constant)");
                b.statement("map.put(constant, index)");
                b.startReturn().string("index").end();

                return ex;
            }

            private CodeExecutableElement createAllocateSlot() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(short.class),
                                "allocateSlot");
                CodeTreeBuilder doc = ex.createDocBuilder();
                doc.startJavadoc();
                doc.string("Allocates a slot for a constant which will be manually added to the constant pool later.");
                doc.newLine();
                doc.end();

                CodeTreeBuilder b = ex.createBuilder();

                b.declaration(type(short.class), "index", safeCastShort("constants.size()"));
                b.statement("constants.add(null)");
                b.startReturn().string("index").end();

                return ex;
            }

            private CodeExecutableElement createToArray() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), new ArrayCodeTypeMirror(type(Object.class)), "toArray");

                CodeTreeBuilder b = ex.createBuilder();
                b.startReturn().string("constants.toArray()").end();

                return ex;
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
                    this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), scopeDataType.asType(), "scope"));
                }

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
                b.startReturn().string("frameIndex - USER_LOCALS_START_INDEX").end();
                return ex;
            }

            private CodeExecutableElement createGetLocalIndex() {
                CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeLocal, "getLocalIndex");
                CodeTreeBuilder b = ex.createBuilder();
                b.startReturn().string("localIndex").end();
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
                CodeTree tree = constructor.getBodyTree();
                CodeTreeBuilder b = constructor.createBuilder();
                b.startStatement().startSuperCall().staticReference(bytecodeRootNodesImpl.asType(), "VISIBLE_TOKEN").end().end();
                b.tree(tree);
                this.add(constructor);

                this.add(createIsDefined());
                this.add(createEquals());
                this.add(createHashCode());
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

    final class BytecodeRootNodesImplElement extends CodeTypeElement {

        private CodeTypeElement updateReason;

        BytecodeRootNodesImplElement() {
            super(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "BytecodeRootNodesImpl");
        }

        void lazyInit() {
            this.setSuperClass(generic(types.BytecodeRootNodes, model.templateType.asType()));
            this.setEnclosingElement(BytecodeRootNodeElement.this);
            this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(Object.class), "VISIBLE_TOKEN")).createInitBuilder().string("TOKEN");
            this.add(compFinal(new CodeVariableElement(Set.of(PRIVATE, VOLATILE), type(long.class), "encoding")));

            this.updateReason = this.add(createUpdateReason());
            this.add(createConstructor());
            this.add(createReparseImpl());
            this.add(createPerformUpdate());
            this.add(createSetNodes());
            this.add(createGetParserImpl());
            this.add(createValidate());
            this.add(createGetLanguage());
            this.add(createIsParsed());

            if (model.enableSerialization) {
                this.add(createSerialize());
            }
        }

        private CodeTypeElement createUpdateReason() {
            DeclaredType charSequence = (DeclaredType) type(CharSequence.class);
            CodeTypeElement reason = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "UpdateReason");
            reason.getImplements().add(charSequence);

            reason.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(boolean.class), "newSources"));
            reason.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(int.class), "newInstrumentations"));
            reason.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(int.class), "newTags"));

            reason.add(GeneratorUtils.createConstructorUsingFields(Set.of(), reason));

            CodeExecutableElement length = reason.add(GeneratorUtils.override(charSequence, "length"));
            length.createBuilder().startReturn().string("toString().length()").end();

            CodeExecutableElement charAt = reason.add(GeneratorUtils.override(charSequence, "charAt", new String[]{"index"}));
            charAt.createBuilder().startReturn().string("toString().charAt(index)").end();

            CodeExecutableElement subSequence = reason.add(GeneratorUtils.override(charSequence, "subSequence", new String[]{"start", "end"}));
            subSequence.createBuilder().startReturn().string("toString().subSequence(start, end)").end();

            CodeExecutableElement toString = reason.add(GeneratorUtils.override(charSequence, "toString"));
            CodeTreeBuilder b = toString.createBuilder();
            b.startStatement().type(type(StringBuilder.class)).string(" message = ").startNew(type(StringBuilder.class)).end().end();
            String message = String.format("%s requested ", ElementUtils.getSimpleName(model.getTemplateType()));
            b.startStatement().startCall("message", "append").doubleQuote(message).end().end();

            b.declaration(type(String.class), "sep", "\"\"");

            b.startIf().string("newSources").end().startBlock();
            message = "SourceInformation";
            b.startStatement().startCall("message", "append").doubleQuote(message).end().end();
            b.startAssign("sep").doubleQuote(", ").end();
            b.end();

            if (!model.getInstrumentations().isEmpty()) {
                b.startIf().string("newInstrumentations != 0").end().startBlock();
                for (CustomOperationModel instrumentation : model.getInstrumentations()) {
                    int index = instrumentation.operation.instrumentationIndex;
                    b.startIf().string("(newInstrumentations & 0x").string(Integer.toHexString(1 << index)).string(") != 0").end().startBlock();
                    b.startStatement().startCall("message", "append").string("sep").end().end();
                    b.startStatement().startCall("message", "append").doubleQuote("Instrumentation[" + instrumentation.operation.name + "]").end().end();
                    b.startAssign("sep").doubleQuote(", ").end();
                    b.end();
                }
                b.end();
            }

            if (!model.getProvidedTags().isEmpty()) {
                b.startIf().string("newTags != 0").end().startBlock();
                int index = 0;
                for (TypeMirror tag : model.getProvidedTags()) {
                    b.startIf().string("(newTags & 0x").string(Integer.toHexString(1 << index)).string(") != 0").end().startBlock();
                    b.startStatement().startCall("message", "append").string("sep").end().end();
                    b.startStatement().startCall("message", "append").doubleQuote("Tag[" + ElementUtils.getSimpleName(tag) + "]").end().end();
                    b.startAssign("sep").doubleQuote(", ").end();
                    b.end();
                    index++;
                }
                b.end();
            }

            b.startStatement().startCall("message", "append").doubleQuote(".").end().end();
            b.statement("return message.toString()");
            return reason;

        }

        private CodeExecutableElement createConstructor() {
            CodeExecutableElement ctor = new CodeExecutableElement(null, "BytecodeRootNodesImpl");
            ctor.addParameter(new CodeVariableElement(parserType, "generator"));
            ctor.addParameter(new CodeVariableElement(types.BytecodeConfig, "config"));
            CodeTreeBuilder b = ctor.createBuilder();
            b.statement("super(VISIBLE_TOKEN, generator)");
            b.startAssign("this.encoding");
            b.startStaticCall(configEncoder.asType(), "decode").string("config").end();
            b.end();

            return ctor;
        }

        private CodeExecutableElement createReparseImpl() {
            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeRootNodes, "updateImpl", new String[]{"encoder", "encoding"});
            mergeSuppressWarnings(ex, "hiding");
            CodeTreeBuilder b = ex.createBuilder();
            b.startDeclaration(type(long.class), "maskedEncoding");
            b.startStaticCall(configEncoder.asType(), "decode").string("encoder").string("encoding").end();
            b.end();
            b.declaration(type(long.class), "oldEncoding", "this.encoding");
            b.declaration(type(long.class), "newEncoding", "maskedEncoding | oldEncoding");

            b.startIf().string("(oldEncoding | newEncoding) == oldEncoding").end().startBlock();
            b.returnFalse();
            b.end();

            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
            b.statement("return performUpdate(maskedEncoding)");

            return ex;
        }

        private CodeExecutableElement createPerformUpdate() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, Modifier.SYNCHRONIZED), type(boolean.class), "performUpdate");
            ex.addParameter(new CodeVariableElement(type(long.class), "maskedEncoding"));
            ex.getModifiers().add(Modifier.SYNCHRONIZED);
            CodeTreeBuilder b = ex.createBuilder();

            b.tree(createNeverPartOfCompilation());
            b.declaration(type(long.class), "oldEncoding", "this.encoding");
            b.declaration(type(long.class), "newEncoding", "maskedEncoding | oldEncoding");
            b.startIf().string("(oldEncoding | newEncoding) == oldEncoding").end().startBlock();
            b.lineComment("double checked locking");
            b.returnFalse();
            b.end();

            b.declaration(type(boolean.class), "oldSources", "(oldEncoding & 0b1) != 0");
            b.declaration(type(int.class), "oldInstrumentations", "(int)((oldEncoding >> " + INSTRUMENTATION_OFFSET + ") & 0x7FFF_FFFF)");
            b.declaration(type(int.class), "oldTags", "(int)((oldEncoding >> " + TAG_OFFSET + ") & 0xFFFF_FFFF)");

            b.declaration(type(boolean.class), "newSources", "(newEncoding & 0b1) != 0");
            b.declaration(type(int.class), "newInstrumentations", "(int)((newEncoding >> " + INSTRUMENTATION_OFFSET + ") & 0x7FFF_FFFF)");
            b.declaration(type(int.class), "newTags", "(int)((newEncoding >> " + TAG_OFFSET + ") & 0xFFFF_FFFF)");

            b.statement("boolean needsBytecodeReparse = newInstrumentations != oldInstrumentations || newTags != oldTags");
            b.statement("boolean needsSourceReparse = newSources != oldSources || (needsBytecodeReparse && newSources)");

            b.startIf().string("!needsBytecodeReparse && !needsSourceReparse").end().startBlock();
            b.statement("return false");
            b.end();

            b.declaration(parserType, "parser", "getParserImpl()");

            b.startStatement().type(updateReason.asType()).string(" reason = ").startNew(updateReason.asType());
            b.string("oldSources != newSources");
            b.string("newInstrumentations & ~oldInstrumentations");
            b.string("newTags & ~oldTags");
            b.end().end();

            // When we reparse, we add metadata to the existing nodes. The builder gets them here.
            b.declaration(builder.getSimpleName().toString(), "builder",
                            b.create().startNew(builder.getSimpleName().toString()).string("this").string("needsBytecodeReparse").string("newTags").string("newInstrumentations").string(
                                            "needsSourceReparse").string("reason").end().build());

            b.startFor().type(model.templateType.asType()).string(" node : nodes").end().startBlock();
            b.startStatement().startCall("builder.builtNodes.add");
            b.startGroup().cast(BytecodeRootNodeElement.this.asType()).string("node").end();
            b.end(2);
            b.end(2);

            b.startStatement().startCall("parser", "parse").string("builder").end(2);
            b.startStatement().startCall("builder", "finish").end(2);

            b.statement("this.encoding = newEncoding");
            b.statement("return true");

            return ex;
        }

        private CodeExecutableElement createSetNodes() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "setNodes");
            ex.addParameter(new CodeVariableElement(arrayOf(BytecodeRootNodeElement.this.asType()), "nodes"));

            CodeTreeBuilder b = ex.createBuilder();
            b.startIf().string("this.nodes != null").end().startBlock();
            b.startThrow().startNew(type(AssertionError.class)).end().end();
            b.end();

            b.statement("this.nodes = nodes");
            b.startFor().type(BytecodeRootNodeElement.this.asType()).string(" node : nodes").end().startBlock();
            b.startIf().string("node.getRootNodes() != this").end().startBlock();
            b.startThrow().startNew(type(AssertionError.class)).end().end();
            b.end();
            b.startIf().string("node != nodes[node.buildIndex]").end().startBlock();
            b.startThrow().startNew(type(AssertionError.class)).end().end();
            b.end();
            b.end();

            return ex;
        }

        private CodeExecutableElement createGetParserImpl() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), parserType, "getParserImpl");
            mergeSuppressWarnings(ex, "unchecked");
            CodeTreeBuilder b = ex.createBuilder();

            b.startReturn();
            b.cast(parserType);
            b.startCall("super.getParser");
            b.end();
            b.end();

            return ex;
        }

        private CodeExecutableElement createValidate() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(boolean.class), "validate");
            CodeTreeBuilder b = ex.createBuilder();

            b.startFor().type(model.getTemplateType().asType()).string(" node : nodes").end().startBlock();
            b.startStatement().string("(").cast(BytecodeRootNodeElement.this.asType(), "node").string(")").string(".getBytecodeNodeImpl().validateBytecodes()").end();
            b.end();

            b.statement("return true");
            return ex;
        }

        private CodeExecutableElement createGetLanguage() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), model.languageClass, "getLanguage");
            CodeTreeBuilder b = ex.createBuilder();

            b.startIf().string("nodes.length == 0").end().startBlock();
            b.startReturn().string("null").end();
            b.end();
            b.startReturn().startCall("nodes[0].getLanguage");
            b.typeLiteral(model.languageClass);
            b.end(2);

            return ex;
        }

        public CodeExecutableElement createIsParsed() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(boolean.class), "isParsed");
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn().string("nodes != null").end();
            return ex;
        }

        private CodeExecutableElement createSerialize() {
            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeRootNodes, "serialize", new String[]{"buffer", "callback"});
            mergeSuppressWarnings(ex, "cast");

            addJavadoc(ex, """
                            Serializes the given bytecode nodes
                            All metadata (e.g., source info) is serialized (even if it has not yet been parsed).
                            <p>
                            This method serializes the root nodes with their current field values.

                            @param buffer the buffer to write the byte output to.
                            @param callback the language-specific serializer for constants in the bytecode.
                            """);

            CodeTreeBuilder b = ex.createBuilder();

            b.declaration(generic(ArrayList.class, model.getTemplateType().asType()), "existingNodes", "new ArrayList<>(nodes.length)");
            b.startFor().string("int i = 0; i < nodes.length; i++").end().startBlock();
            b.startStatement().startCall("existingNodes", "add");
            b.startGroup().cast(BytecodeRootNodeElement.this.asType()).string("nodes[i]").end();
            b.end(2);
            b.end();

            b.startStatement();
            b.startStaticCall(BytecodeRootNodeElement.this.asType(), "doSerialize");
            b.string("buffer");
            b.string("callback");

            // Create a new Builder with this BytecodeRootNodes instance.
            b.startNew("Builder");
            b.string("getLanguage()");
            b.string("this");
            b.staticReference(types.BytecodeConfig, "COMPLETE");
            b.end();

            b.string("existingNodes");

            b.end(2);

            return ex;
        }
    }

    final class FrameTagConstantsElement extends CodeTypeElement {
        private final Map<TypeMirror, VariableElement> mapping;

        FrameTagConstantsElement() {
            super(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "FrameTags");

            // List of FrameSlotKinds we need to declare constants for.
            Map<String, TypeMirror> frameTypes = new HashMap<>();
            for (TypeMirror boxingEliminatedType : model.boxingEliminatedTypes) {
                frameTypes.put(ElementUtils.firstLetterUpperCase(ElementUtils.getSimpleName(boxingEliminatedType)), boxingEliminatedType);
            }
            frameTypes.put("Object", declaredType(Object.class));
            frameTypes.put("Illegal", null);

            // Construct the constants, iterating over the enum fields to find the tag values.
            Map<TypeMirror, VariableElement> result = new HashMap<>();
            TypeElement frameSlotKindType = ElementUtils.castTypeElement(types.FrameSlotKind);
            int index = 0;
            for (VariableElement var : ElementFilter.fieldsIn(CompilerFactory.getCompiler(frameSlotKindType).getAllMembersInDeclarationOrder(context.getEnvironment(), frameSlotKindType))) {
                if (var.getKind() != ElementKind.ENUM_CONSTANT) {
                    continue;
                }
                String frameSlotKind = var.getSimpleName().toString();
                if (frameTypes.containsKey(frameSlotKind)) {
                    CodeVariableElement fld = new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(byte.class), frameSlotKind.toUpperCase());
                    fld.createInitBuilder().string(index + " /* FrameSlotKind." + frameSlotKind + ".tag */");
                    this.add(fld);
                    result.put(frameTypes.remove(frameSlotKind), fld);
                }
                index++;
            }
            if (!frameTypes.isEmpty()) {
                throw new AssertionError(String.format("Could not find a FrameSlotKind for some types: %s", frameTypes.keySet()));
            }
            mapping = result;
        }

        private VariableElement get(TypeMirror type) {
            return mapping.get(type);
        }

        private VariableElement getObject() {
            return mapping.get(declaredType(Object.class));
        }

        private VariableElement getIllegal() {
            return mapping.get(null);
        }
    }

    // Generates an Instructions class with constants for each instruction.
    final class InstructionConstantsElement extends CodeTypeElement {
        InstructionConstantsElement() {
            super(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "Instructions");
        }

        void lazyInit() {
            for (InstructionModel instruction : BytecodeRootNodeElement.this.model.getInstructions()) {
                CodeVariableElement fld = new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(short.class), instruction.getConstantName());
                fld.createInitBuilder().string(instruction.getId()).end();
                fld.createDocBuilder().startDoc().lines(instruction.pp()).end(2);
                this.add(fld);
            }
        }
    }

    final class OperationConstantsElement extends CodeTypeElement {

        OperationConstantsElement() {
            super(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "Operations");
        }

        void lazyInit() {
            for (OperationModel operation : model.getOperations()) {
                CodeVariableElement fld = new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), operation.getConstantName());
                fld.createInitBuilder().string(operation.id).end();
                this.add(fld);
            }
        }
    }

    final class ExceptionHandlerImplElement extends CodeTypeElement {

        ExceptionHandlerImplElement() {
            super(Set.of(PRIVATE, STATIC, FINAL),
                            ElementKind.CLASS, null, "ExceptionHandlerImpl");
            this.setSuperClass(types.ExceptionHandler);

            this.add(new CodeVariableElement(Set.of(FINAL), abstractBytecodeNode.asType(), "bytecode"));
            this.add(new CodeVariableElement(Set.of(FINAL), type(int.class), "baseIndex"));

            CodeExecutableElement constructor = this.add(createConstructorUsingFields(Set.of(), this, null));
            CodeTree tree = constructor.getBodyTree();
            CodeTreeBuilder b = constructor.createBuilder();
            b.startStatement().startSuperCall().staticReference(bytecodeRootNodesImpl.asType(), "VISIBLE_TOKEN").end().end();
            b.tree(tree);
            this.add(createGetKind());
            this.add(createGetStartBytecodeIndex());
            this.add(createGetEndBytecodeIndex());
            this.add(createGetHandlerBytecodeIndex());
            this.add(createGetTagTree());
        }

        private CodeExecutableElement createGetKind() {
            CodeExecutableElement ex = GeneratorUtils.override(types.ExceptionHandler, "getKind");
            CodeTreeBuilder b = ex.createBuilder();
            if (hasSpecialHandlers()) {
                b.startSwitch();
                b.string("bytecode.handlers[baseIndex + EXCEPTION_HANDLER_OFFSET_KIND]");
                b.end().startBlock();
                if (model.enableTagInstrumentation) {
                    b.startCase().string("HANDLER_TAG_EXCEPTIONAL").end().startCaseBlock();
                    b.startReturn().staticReference(types.ExceptionHandler_HandlerKind, "TAG").end();
                    b.end();
                }
                if (model.epilogExceptional != null) {
                    b.startCase().string("HANDLER_EPILOG_EXCEPTIONAL").end().startCaseBlock();
                    b.startReturn().staticReference(types.ExceptionHandler_HandlerKind, "EPILOG").end();
                    b.end();
                }
                b.caseDefault().startCaseBlock();
                b.startReturn().staticReference(types.ExceptionHandler_HandlerKind, "CUSTOM").end();
                b.end();
                b.end(); // switch block
            } else {
                b.startReturn().staticReference(types.ExceptionHandler_HandlerKind, "CUSTOM").end();
            }
            return ex;
        }

        private boolean hasSpecialHandlers() {
            return model.enableTagInstrumentation || model.epilogExceptional != null;
        }

        private CodeExecutableElement createGetStartBytecodeIndex() {
            CodeExecutableElement ex = GeneratorUtils.override(types.ExceptionHandler, "getStartBytecodeIndex");
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return bytecode.handlers[baseIndex + EXCEPTION_HANDLER_OFFSET_START_BCI]");
            return ex;
        }

        private CodeExecutableElement createGetEndBytecodeIndex() {
            CodeExecutableElement ex = GeneratorUtils.override(types.ExceptionHandler, "getEndBytecodeIndex");
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return bytecode.handlers[baseIndex + EXCEPTION_HANDLER_OFFSET_END_BCI]");
            return ex;
        }

        private CodeExecutableElement createGetHandlerBytecodeIndex() {
            CodeExecutableElement ex = GeneratorUtils.override(types.ExceptionHandler, "getHandlerBytecodeIndex");
            CodeTreeBuilder b = ex.createBuilder();

            if (hasSpecialHandlers()) {
                b.startSwitch();
                b.string("getKind()");
                b.end().startBlock();
                if (model.enableTagInstrumentation) {
                    b.startCase().string("TAG").end();
                }
                if (model.epilogExceptional != null) {
                    b.startCase().string("EPILOG").end();
                }
                b.startCaseBlock();
                b.statement("return super.getHandlerBytecodeIndex()");
                b.end();
                b.caseDefault().startCaseBlock();
                b.statement("return bytecode.handlers[baseIndex + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI]");
                b.end();
                b.end(); // switch block
            } else {
                b.statement("return bytecode.handlers[baseIndex + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI]");
            }

            return ex;
        }

        private CodeExecutableElement createGetTagTree() {
            CodeExecutableElement ex = GeneratorUtils.override(types.ExceptionHandler, "getTagTree");
            CodeTreeBuilder b = ex.createBuilder();
            if (model.enableTagInstrumentation) {
                b.startIf().string("getKind() == ").staticReference(types.ExceptionHandler_HandlerKind, "TAG").end().startBlock();
                b.declaration(type(int.class), "nodeId", "bytecode.handlers[baseIndex + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI]");
                b.statement("return bytecode.tagRoot.tagNodes[nodeId]");
                b.end().startElseBlock();
                b.statement("return super.getTagTree()");
                b.end();
            } else {
                b.statement("return super.getTagTree()");
            }

            return ex;
        }

    }

    final class ExceptionHandlerListElement extends CodeTypeElement {

        ExceptionHandlerListElement() {
            super(Set.of(PRIVATE, STATIC, FINAL),
                            ElementKind.CLASS, null, "ExceptionHandlerList");
            this.setSuperClass(generic(type(AbstractList.class), types.ExceptionHandler));
            this.add(new CodeVariableElement(Set.of(FINAL), abstractBytecodeNode.asType(), "bytecode"));
            this.add(createConstructorUsingFields(Set.of(), this, null));
            this.add(createGet());
            this.add(createSize());
        }

        private CodeExecutableElement createGet() {
            CodeExecutableElement ex = GeneratorUtils.override(declaredType(List.class), "get", new String[]{"index"}, new TypeMirror[]{type(int.class)});
            ex.setReturnType(types.ExceptionHandler);
            CodeTreeBuilder b = ex.createBuilder();
            b.declaration(type(int.class), "baseIndex", "index * EXCEPTION_HANDLER_LENGTH");
            b.startIf().string("baseIndex < 0 || baseIndex >= bytecode.handlers.length").end().startBlock();
            b.startThrow().startNew(type(IndexOutOfBoundsException.class)).string("String.valueOf(index)").end().end();
            b.end();
            b.startReturn();
            b.startNew("ExceptionHandlerImpl").string("bytecode").string("baseIndex").end();
            b.end();
            return ex;
        }

        private CodeExecutableElement createSize() {
            CodeExecutableElement ex = GeneratorUtils.override(declaredType(List.class), "size");
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return bytecode.handlers.length / EXCEPTION_HANDLER_LENGTH");
            return ex;
        }

    }

    final class SourceInformationImplElement extends CodeTypeElement {

        SourceInformationImplElement() {
            super(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "SourceInformationImpl");
            this.setSuperClass(types.SourceInformation);

            this.add(new CodeVariableElement(Set.of(FINAL), abstractBytecodeNode.asType(), "bytecode"));
            this.add(new CodeVariableElement(Set.of(FINAL), type(int.class), "baseIndex"));

            CodeExecutableElement constructor = this.add(createConstructorUsingFields(Set.of(), this, null));
            CodeTree tree = constructor.getBodyTree();
            CodeTreeBuilder b = constructor.createBuilder();
            b.startStatement().startSuperCall().staticReference(bytecodeRootNodesImpl.asType(), "VISIBLE_TOKEN").end().end();
            b.tree(tree);
            this.add(createGetStartBytecodeIndex());
            this.add(createGetEndBytecodeIndex());
            this.add(createGetSourceSection());
        }

        private CodeExecutableElement createGetStartBytecodeIndex() {
            CodeExecutableElement ex = GeneratorUtils.override(types.SourceInformation, "getStartBytecodeIndex");
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn().string("bytecode.sourceInfo[baseIndex + SOURCE_INFO_OFFSET_START_BCI]").end();
            return ex;
        }

        private CodeExecutableElement createGetEndBytecodeIndex() {
            CodeExecutableElement ex = GeneratorUtils.override(types.SourceInformation, "getEndBytecodeIndex");
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn().string("bytecode.sourceInfo[baseIndex + SOURCE_INFO_OFFSET_END_BCI]").end();
            return ex;
        }

        private CodeExecutableElement createGetSourceSection() {
            CodeExecutableElement ex = GeneratorUtils.override(types.SourceInformation, "getSourceSection");
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return AbstractBytecodeNode.createSourceSection(bytecode.sources, bytecode.sourceInfo, baseIndex)");
            return ex;
        }

    }

    final class SourceInformationListElement extends CodeTypeElement {

        SourceInformationListElement() {
            super(Set.of(PRIVATE, STATIC, FINAL),
                            ElementKind.CLASS, null, "SourceInformationList");
            this.setSuperClass(generic(type(AbstractList.class), types.SourceInformation));
            this.add(new CodeVariableElement(Set.of(FINAL), abstractBytecodeNode.asType(), "bytecode"));
            this.add(createConstructorUsingFields(Set.of(), this, null));
            this.add(createGet());
            this.add(createSize());
        }

        private CodeExecutableElement createGet() {
            CodeExecutableElement ex = GeneratorUtils.override(declaredType(List.class), "get", new String[]{"index"}, new TypeMirror[]{type(int.class)});
            ex.setReturnType(types.SourceInformation);
            CodeTreeBuilder b = ex.createBuilder();
            b.declaration(type(int.class), "baseIndex", "index * SOURCE_INFO_LENGTH");
            b.startIf().string("baseIndex < 0 || baseIndex >= bytecode.sourceInfo.length").end().startBlock();
            b.startThrow().startNew(type(IndexOutOfBoundsException.class)).string("String.valueOf(index)").end().end();
            b.end();
            b.startReturn();
            b.startNew("SourceInformationImpl").string("bytecode").string("baseIndex").end();
            b.end();
            return ex;
        }

        private CodeExecutableElement createSize() {
            CodeExecutableElement ex = GeneratorUtils.override(declaredType(List.class), "size");
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return bytecode.sourceInfo.length / SOURCE_INFO_LENGTH");
            return ex;
        }

    }

    final class SourceInformationTreeImplElement extends CodeTypeElement {

        SourceInformationTreeImplElement() {
            super(Set.of(PRIVATE, STATIC, FINAL),
                            ElementKind.CLASS, null, "SourceInformationTreeImpl");
            this.setSuperClass(types.SourceInformationTree);

            this.add(new CodeVariableElement(Set.of(FINAL, STATIC), type(int.class), "UNAVAILABLE_ROOT")).createInitBuilder().string("-1");
            this.add(new CodeVariableElement(Set.of(FINAL), abstractBytecodeNode.asType(), "bytecode"));
            this.add(new CodeVariableElement(Set.of(FINAL), type(int.class), "baseIndex"));
            this.add(new CodeVariableElement(Set.of(FINAL), generic(List.class, types.SourceInformationTree), "children"));

            CodeExecutableElement constructor = this.add(createConstructorUsingFields(Set.of(), this, null, Set.of("children")));
            CodeTree tree = constructor.getBodyTree();
            CodeTreeBuilder b = constructor.createBuilder();
            b.startStatement().startSuperCall().staticReference(bytecodeRootNodesImpl.asType(), "VISIBLE_TOKEN").end().end();
            b.tree(tree);
            // We prepend items during parsing. Use a linked list for constant time prepends.
            b.startAssign("this.children").startNew(generic(type(LinkedList.class), types.SourceInformationTree)).end(2);

            this.add(createGetStartBytecodeIndex());
            this.add(createGetEndBytecodeIndex());
            this.add(createGetSourceSection());
            this.add(createGetChildren());
            this.add(createContains());
            this.add(createParse());
        }

        private CodeExecutableElement createGetStartBytecodeIndex() {
            CodeExecutableElement ex = GeneratorUtils.override(types.SourceInformation, "getStartBytecodeIndex");
            CodeTreeBuilder b = ex.createBuilder();
            b.startIf().string("baseIndex == UNAVAILABLE_ROOT").end().startBlock();
            b.startReturn().string("0").end();
            b.end();
            b.startReturn().string("bytecode.sourceInfo[baseIndex + SOURCE_INFO_OFFSET_START_BCI]").end();
            return ex;
        }

        private CodeExecutableElement createGetEndBytecodeIndex() {
            CodeExecutableElement ex = GeneratorUtils.override(types.SourceInformation, "getEndBytecodeIndex");
            CodeTreeBuilder b = ex.createBuilder();
            b.startIf().string("baseIndex == UNAVAILABLE_ROOT").end().startBlock();
            b.startReturn().string("bytecode.bytecodes.length").end();
            b.end();
            b.startReturn().string("bytecode.sourceInfo[baseIndex + SOURCE_INFO_OFFSET_END_BCI]").end();
            return ex;
        }

        private CodeExecutableElement createGetSourceSection() {
            CodeExecutableElement ex = GeneratorUtils.override(types.SourceInformation, "getSourceSection");
            CodeTreeBuilder b = ex.createBuilder();
            b.startIf().string("baseIndex == UNAVAILABLE_ROOT").end().startBlock();
            b.startReturn().string("null").end();
            b.end();
            b.statement("return AbstractBytecodeNode.createSourceSection(bytecode.sources, bytecode.sourceInfo, baseIndex)");
            return ex;
        }

        private CodeExecutableElement createGetChildren() {
            CodeExecutableElement ex = GeneratorUtils.override(types.SourceInformationTree, "getChildren");
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return children");
            return ex;
        }

        private CodeExecutableElement createContains() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(boolean.class), "contains");
            ex.addParameter(new CodeVariableElement(this.asType(), "other"));
            CodeTreeBuilder b = ex.createBuilder();
            b.startIf().string("baseIndex == UNAVAILABLE_ROOT").end().startBlock();
            b.startReturn().string("true").end();
            b.end();
            b.statement("return this.getStartBytecodeIndex() <= other.getStartBytecodeIndex() && other.getEndBytecodeIndex() <= this.getEndBytecodeIndex()");
            return ex;
        }

        private CodeExecutableElement createParse() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), types.SourceInformationTree, "parse");
            ex.addParameter(new CodeVariableElement(abstractBytecodeNode.asType(), "bytecode"));

            CodeTreeBuilder b = ex.createBuilder();
            /**
             * This algorithm reconstructs the source information tree in a single linear pass of
             * the source info table.
             */
            b.declaration(arrayOf(type(int.class)), "sourceInfo", "bytecode.sourceInfo");

            b.startIf().string("sourceInfo.length == 0").end().startBlock();
            b.statement("return null");
            b.end();

            b.lineComment("Create a synthetic root node that contains all other SourceInformationTrees.");
            b.startDeclaration(this.asType(), "root");
            b.startNew(this.asType()).string("bytecode").string("UNAVAILABLE_ROOT").end();
            b.end();

            b.declaration(type(int.class), "baseIndex", "sourceInfo.length");
            b.declaration(this.asType(), "current", "root");
            b.declaration(generic(ArrayDeque.class, this.asType()), "stack", "new ArrayDeque<>()");
            b.startDoBlock();
            // Create the next node.
            b.statement("baseIndex -= SOURCE_INFO_LENGTH");
            b.startDeclaration(this.asType(), "newNode");
            b.startNew(this.asType()).string("bytecode").string("baseIndex").end();
            b.end();

            // Find the node's parent.
            b.startWhile().string("!current.contains(newNode)").end().startBlock();
            // If newNode is not contained in current, then no more entries belong to current (we
            // are done parsing it). newNode must be a child of some other node on the stack.
            b.statement("current = stack.pop()");
            b.end();

            // Link up the child and continue parsing.
            b.statement("current.children.addFirst(newNode)");
            b.statement("stack.push(current)");
            b.statement("current = newNode");

            b.end().startDoWhile().string("baseIndex > 0").end();

            b.startIf().string("root.getChildren().size() == 1").end().startBlock();
            b.lineComment("If there is an actual root source section, ignore the synthetic root we created.");
            b.statement("return root.getChildren().getFirst()");
            b.end().startElseBlock();
            b.statement("return root");
            b.end();
            return withTruffleBoundary(ex);
        }

    }

    final class LocalVariableImplElement extends CodeTypeElement {

        LocalVariableImplElement() {
            super(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "LocalVariableImpl");
            this.setSuperClass(types.LocalVariable);

            this.add(new CodeVariableElement(Set.of(FINAL), abstractBytecodeNode.asType(), "bytecode"));
            this.add(new CodeVariableElement(Set.of(FINAL), type(int.class), "baseIndex"));

            CodeExecutableElement constructor = this.add(createConstructorUsingFields(Set.of(), this, null));
            CodeTree tree = constructor.getBodyTree();
            CodeTreeBuilder b = constructor.createBuilder();
            b.startStatement().startSuperCall().staticReference(bytecodeRootNodesImpl.asType(), "VISIBLE_TOKEN").end().end();
            b.tree(tree);

            if (model.enableBlockScoping) {
                this.add(createGetStartIndex());
                this.add(createGetEndIndex());
            }
            this.add(createGetInfo());
            this.add(createGetName());
            this.add(createGetLocalIndex());
            this.add(createGetLocalOffset());
            this.add(createGetTypeProfile());
        }

        private CodeExecutableElement createGetStartIndex() {
            CodeExecutableElement ex = GeneratorUtils.override(types.LocalVariable, "getStartIndex");
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return bytecode.locals[baseIndex + LOCALS_OFFSET_START_BCI]");
            return ex;
        }

        private CodeExecutableElement createGetEndIndex() {
            CodeExecutableElement ex = GeneratorUtils.override(types.LocalVariable, "getEndIndex");
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return bytecode.locals[baseIndex + LOCALS_OFFSET_END_BCI]");
            return ex;
        }

        private CodeExecutableElement createGetInfo() {
            CodeExecutableElement ex = GeneratorUtils.override(types.LocalVariable, "getInfo");
            CodeTreeBuilder b = ex.createBuilder();

            b.declaration(type(int.class), "infoId", "bytecode.locals[baseIndex + LOCALS_OFFSET_INFO]");
            b.startIf().string("infoId == -1").end().startBlock();
            b.returnNull();
            b.end().startElseBlock();
            b.startReturn().tree(readConst("infoId", "bytecode.constants")).end();
            b.end();
            return ex;
        }

        private CodeExecutableElement createGetName() {
            CodeExecutableElement ex = GeneratorUtils.override(types.LocalVariable, "getName");
            CodeTreeBuilder b = ex.createBuilder();

            b.declaration(type(int.class), "nameId", "bytecode.locals[baseIndex + LOCALS_OFFSET_NAME]");
            b.startIf().string("nameId == -1").end().startBlock();
            b.returnNull();
            b.end().startElseBlock();
            b.startReturn().tree(readConst("nameId", "bytecode.constants")).end();
            b.end();
            return ex;
        }

        private CodeExecutableElement createGetLocalIndex() {
            CodeExecutableElement ex = GeneratorUtils.override(types.LocalVariable, "getLocalIndex");
            CodeTreeBuilder b = ex.createBuilder();
            if (model.enableBlockScoping) {
                b.statement("return bytecode.locals[baseIndex + LOCALS_OFFSET_LOCAL_INDEX]");
            } else {
                b.statement("return baseIndex / LOCALS_LENGTH");
            }
            return ex;
        }

        private CodeExecutableElement createGetLocalOffset() {
            CodeExecutableElement ex = GeneratorUtils.override(types.LocalVariable, "getLocalOffset");
            CodeTreeBuilder b = ex.createBuilder();
            if (model.enableBlockScoping) {
                b.statement("return bytecode.locals[baseIndex + LOCALS_OFFSET_FRAME_INDEX] - USER_LOCALS_START_INDEX");
            } else {
                b.statement("return baseIndex / LOCALS_LENGTH");
            }
            return ex;
        }

        private CodeExecutableElement createGetTypeProfile() {
            CodeExecutableElement ex = GeneratorUtils.override(types.LocalVariable, "getTypeProfile");
            CodeTreeBuilder b = ex.createBuilder();

            if (model.usesBoxingElimination()) {
                b.declaration(type(byte[].class), "localTags", "bytecode.getLocalTags()");
                b.startIf().string("localTags == null").end().startBlock();
                b.returnNull();
                b.end();
                b.statement("return FrameSlotKind.fromTag(localTags[getLocalIndex()])");
            } else {
                b.returnNull();
            }
            return ex;
        }

    }

    final class LocalVariableListElement extends CodeTypeElement {

        LocalVariableListElement() {
            super(Set.of(PRIVATE, STATIC, FINAL),
                            ElementKind.CLASS, null, "LocalVariableList");
            this.setSuperClass(generic(type(AbstractList.class), types.LocalVariable));
            this.add(new CodeVariableElement(Set.of(FINAL), abstractBytecodeNode.asType(), "bytecode"));
            this.add(createConstructorUsingFields(Set.of(), this, null));
            this.add(createGet());
            this.add(createSize());
        }

        private CodeExecutableElement createGet() {
            CodeExecutableElement ex = GeneratorUtils.override(declaredType(List.class), "get", new String[]{"index"}, new TypeMirror[]{type(int.class)});
            ex.setReturnType(types.LocalVariable);
            CodeTreeBuilder b = ex.createBuilder();
            b.declaration(type(int.class), "baseIndex", "index * LOCALS_LENGTH");
            b.startIf().string("baseIndex < 0 || baseIndex >= bytecode.locals.length").end().startBlock();
            b.startThrow().startNew(type(IndexOutOfBoundsException.class)).string("String.valueOf(index)").end().end();
            b.end();
            b.startReturn();
            b.startNew("LocalVariableImpl").string("bytecode").string("baseIndex").end();
            b.end();
            return ex;
        }

        private CodeExecutableElement createSize() {
            CodeExecutableElement ex = GeneratorUtils.override(declaredType(List.class), "size");
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return bytecode.locals.length / LOCALS_LENGTH");
            return ex;
        }

    }

    final class InstructionImplElement extends CodeTypeElement {

        private CodeTypeElement abstractArgument;

        InstructionImplElement() {
            super(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "InstructionImpl");
            this.setSuperClass(types.Instruction);
        }

        void lazyInit() {
            this.add(new CodeVariableElement(Set.of(FINAL), abstractBytecodeNode.asType(), "bytecode"));
            this.add(new CodeVariableElement(Set.of(FINAL), type(int.class), "bci"));
            this.add(new CodeVariableElement(Set.of(FINAL), type(int.class), "opcode"));

            CodeExecutableElement constructor = this.add(createConstructorUsingFields(Set.of(), this, null));
            CodeTree tree = constructor.getBodyTree();
            CodeTreeBuilder b = constructor.createBuilder();
            b.startStatement().startSuperCall().staticReference(bytecodeRootNodesImpl.asType(), "VISIBLE_TOKEN").end().end();
            b.tree(tree);

            abstractArgument = this.add(new AbstractArgumentElement());
            this.add(createGetBytecodeIndex());
            this.add(createGetBytecodeNode());
            this.add(createGetOperationCode());
            this.add(createGetLength());
            this.add(createGetArguments());
            this.add(createGetName());
            this.add(createIsInstrumentation());
            this.add(createNext());

            Set<String> generated = new HashSet<>();
            for (ImmediateKind kind : ImmediateKind.values()) {
                if (kind == ImmediateKind.LOCAL_INDEX && !model.localAccessesNeedLocalIndex() && !model.materializedLocalAccessesNeedLocalIndex()) {
                    // Only generate immediate class for LocalIndex when needed.
                    continue;
                }

                String className = getImmediateClassName(kind);
                if (generated.contains(className)) {
                    continue;
                }
                if (kind == ImmediateKind.TAG_NODE && !model.enableTagInstrumentation) {
                    continue;
                }
                CodeTypeElement implType = this.add(new ArgumentElement(kind));
                abstractArgument.getPermittedSubclasses().add(implType.asType());
                generated.add(className);
            }
        }

        private CodeExecutableElement createGetBytecodeIndex() {
            CodeExecutableElement ex = GeneratorUtils.override(types.Instruction, "getBytecodeIndex");
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return bci");
            return ex;
        }

        private CodeExecutableElement createNext() {
            CodeExecutableElement ex = GeneratorUtils.override(types.Instruction, "next");
            CodeTreeBuilder b = ex.createBuilder();
            b.declaration(type(int.class), "nextBci", "getNextBytecodeIndex()");
            b.startIf().string("nextBci >= bytecode.bytecodes.length").end().startBlock();
            b.returnNull();
            b.end();
            b.startReturn().startNew(this.asType()).string("bytecode").string("nextBci").string("bytecode.readValidBytecode(bytecode.bytecodes, nextBci)").end().end();
            return ex;
        }

        private CodeExecutableElement createGetBytecodeNode() {
            CodeExecutableElement ex = GeneratorUtils.override(types.Instruction, "getBytecodeNode");
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return bytecode");
            return ex;
        }

        private CodeExecutableElement createGetName() {
            CodeExecutableElement ex = GeneratorUtils.override(types.Instruction, "getName");
            CodeTreeBuilder b = ex.createBuilder();
            b.startSwitch().string("opcode").end().startBlock();
            // Pop any value produced by a transparent operation's child.
            for (InstructionModel instruction : model.getInstructions()) {
                b.startCase().tree(createInstructionConstant(instruction)).end();
                b.startCaseBlock();
                b.startReturn().doubleQuote(instruction.name).end();
                b.end();
            }
            b.end();
            b.tree(GeneratorUtils.createShouldNotReachHere("Invalid opcode"));
            return ex;
        }

        private CodeExecutableElement createIsInstrumentation() {
            CodeExecutableElement ex = GeneratorUtils.override(types.Instruction, "isInstrumentation");
            CodeTreeBuilder b = ex.createBuilder();

            Map<Boolean, List<InstructionModel>> grouped = groupInstructionsByInstrumentation(model.getInstructions());

            if (!grouped.containsKey(true)) {
                // Simplification: no instruction is an instrumentation instruction.
                b.startReturn().string("false").end();
                return ex;
            }

            b.startSwitch().string("opcode").end().startBlock();
            for (var entry : grouped.entrySet()) {
                for (InstructionModel instruction : entry.getValue()) {
                    b.startCase().tree(createInstructionConstant(instruction)).end();
                }
                b.startCaseBlock();
                b.startReturn().string(Boolean.toString(entry.getKey())).end();
                b.end();
            }

            b.end();
            b.tree(GeneratorUtils.createShouldNotReachHere("Invalid opcode"));
            return ex;
        }

        private CodeExecutableElement createGetLength() {
            CodeExecutableElement ex = GeneratorUtils.override(types.Instruction, "getLength");
            CodeTreeBuilder b = ex.createBuilder();
            b.startSwitch().string("opcode").end().startBlock();
            // Pop any value produced by a transparent operation's child.
            for (var instructions : groupInstructionsByLength(model.getInstructions())) {
                for (InstructionModel instruction : instructions) {
                    b.startCase().tree(createInstructionConstant(instruction)).end();
                }
                InstructionModel instruction = instructions.get(0);
                b.startCaseBlock();
                b.startReturn().string(instruction.getInstructionLength()).end();
                b.end();
            }
            b.end();
            b.tree(GeneratorUtils.createShouldNotReachHere("Invalid opcode"));
            return ex;
        }

        private CodeExecutableElement createGetArguments() {
            CodeExecutableElement ex = GeneratorUtils.override(types.Instruction, "getArguments");
            CodeTreeBuilder b = ex.createBuilder();
            b.startSwitch().string("opcode").end().startBlock();
            // Pop any value produced by a transparent operation's child.
            for (var instructions : groupInstructionsByImmediates(model.getInstructions())) {
                for (InstructionModel instruction : instructions) {
                    b.startCase().tree(createInstructionConstant(instruction)).end();
                }
                InstructionModel instruction = instructions.get(0);

                b.startCaseBlock();
                b.startReturn().startStaticCall(type(List.class), "of");
                for (InstructionImmediate immediate : instruction.getImmediates()) {
                    b.startGroup();
                    b.newLine();
                    b.startIndention();
                    b.startNew(getImmediateClassName(immediate.kind()));
                    b.string("bytecode");
                    b.doubleQuote(getIntrospectionArgumentName(immediate));
                    b.string("bci + " + immediate.offset());
                    for (String arg : getImmediateArgumentArgs(immediate.kind())) {
                        b.string(arg);
                    }
                    b.end();
                    b.end();
                    b.end();
                }
                b.end().end(); // return

                b.end(); // case block
            }
            b.end();
            b.tree(GeneratorUtils.createShouldNotReachHere("Invalid opcode"));
            return ex;
        }

        private String getIntrospectionArgumentName(InstructionImmediate immediate) {
            if (immediate.kind() == ImmediateKind.FRAME_INDEX) {
                // We expose the frame_index as a local offset, so don't use the immediate name.
                return "local_offset";
            }
            return immediate.name();
        }

        private Map<Boolean, List<InstructionModel>> groupInstructionsByInstrumentation(Collection<InstructionModel> models) {
            return models.stream().collect(deterministicGroupingBy(InstructionModel::isInstrumentation));
        }

        private Collection<List<InstructionModel>> groupInstructionsByImmediates(Collection<InstructionModel> models) {
            return models.stream().collect(deterministicGroupingBy((m) -> m.getImmediates())).values().stream().sorted(Comparator.comparingInt((i) -> i.get(0).getId())).toList();
        }

        private CodeExecutableElement createGetOperationCode() {
            CodeExecutableElement ex = GeneratorUtils.override(types.Instruction, "getOperationCode");
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn().string("opcode").end();
            return ex;
        }

        private static String getImmediateClassName(ImmediateKind kind) {
            switch (kind) {
                case BRANCH_PROFILE:
                    return "BranchProfileArgument";
                case BYTECODE_INDEX:
                    return "BytecodeIndexArgument";
                case CONSTANT:
                    return "ConstantArgument";
                case FRAME_INDEX:
                    return "LocalOffsetArgument";
                case LOCAL_INDEX:
                    return "LocalIndexArgument";
                case SHORT:
                case LOCAL_ROOT:
                case STACK_POINTER:
                    return "IntegerArgument";
                case NODE_PROFILE:
                    return "NodeProfileArgument";
                case TAG_NODE:
                    return "TagNodeArgument";
            }
            throw new AssertionError("invalid kind");
        }

        private List<Element> getArgumentFields(ImmediateKind kind) {
            return switch (kind) {
                case SHORT, LOCAL_ROOT, STACK_POINTER -> List.of(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(int.class), "width"));
                default -> List.of();
            };
        }

        private static List<String> getImmediateArgumentArgs(ImmediateKind kind) {
            return switch (kind) {
                case SHORT, LOCAL_ROOT, STACK_POINTER -> List.of(Integer.toString(kind.width.byteSize));
                default -> List.of();
            };
        }

        final class AbstractArgumentElement extends CodeTypeElement {

            AbstractArgumentElement() {
                super(Set.of(PRIVATE, SEALED, STATIC, ABSTRACT),
                                ElementKind.CLASS, null, "AbstractArgument");
                this.setSuperClass(types.Instruction_Argument);
                this.add(new CodeVariableElement(Set.of(FINAL), abstractBytecodeNode.asType(), "bytecode"));
                this.add(new CodeVariableElement(Set.of(FINAL), type(String.class), "name"));
                this.add(new CodeVariableElement(Set.of(FINAL), type(int.class), "bci"));
                CodeExecutableElement constructor = this.add(createConstructorUsingFields(Set.of(), this, null));
                CodeTree tree = constructor.getBodyTree();
                CodeTreeBuilder b = constructor.createBuilder();
                b.startStatement().startSuperCall().staticReference(bytecodeRootNodesImpl.asType(), "VISIBLE_TOKEN").end().end();
                b.tree(tree);

                this.add(new CodeVariableElement(Set.of(PROTECTED, STATIC, FINAL), types.BytecodeDSLAccess, "SAFE_ACCESS")) //
                                .createInitBuilder().tree(createFastAccessFieldInitializer(false));
                this.add(new CodeVariableElement(Set.of(PROTECTED, STATIC, FINAL), types.ByteArraySupport, "SAFE_BYTES")) //
                                .createInitBuilder().startCall("SAFE_ACCESS.getByteArraySupport").end();
                this.add(createGetName());
            }

            private CodeExecutableElement createGetName() {
                CodeExecutableElement ex = GeneratorUtils.override(types.Instruction_Argument, "getName");
                ex.getModifiers().add(Modifier.FINAL);
                CodeTreeBuilder b = ex.createBuilder();
                b.statement("return name");
                return ex;
            }

        }

        final class ArgumentElement extends CodeTypeElement {

            private ImmediateKind immediateKind;

            ArgumentElement(ImmediateKind immediateKind) {
                super(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, getImmediateClassName(immediateKind));
                this.immediateKind = immediateKind;
                this.setSuperClass(abstractArgument.asType());
                this.addAll(getArgumentFields(immediateKind));
                this.add(createConstructorUsingFields(Set.of(), this));
                this.add(createGetKind());

                switch (immediateKind) {
                    case BYTECODE_INDEX:
                        this.add(createAsBytecodeIndex());
                        break;
                    case SHORT:
                    case LOCAL_ROOT:
                    case STACK_POINTER:
                        this.add(createAsInteger());
                        break;
                    case FRAME_INDEX:
                        this.add(createAsLocalOffset());
                        break;
                    case LOCAL_INDEX:
                        this.add(createAsLocalIndex());
                        break;
                    case CONSTANT:
                        this.add(createAsConstant());
                        break;
                    case NODE_PROFILE:
                        this.add(createAsCachedNode());
                        break;
                    case BRANCH_PROFILE:
                        this.add(createAsBranchProfile());
                        break;
                    case TAG_NODE:
                        this.add(createAsTagNode());
                        break;
                    default:
                        throw new AssertionError("Unexpected kind");
                }
            }

            private static String readByteSafe(String array, String index) {
                return String.format("SAFE_BYTES.getByte(%s, %s)", array, index);
            }

            private static String readShortSafe(String array, String index) {
                return String.format("SAFE_BYTES.getShort(%s, %s)", array, index);
            }

            private static String readIntSafe(String array, String index) {
                return String.format("SAFE_BYTES.getInt(%s, %s)", array, index);
            }

            private static String readConstSafe(String index) {
                return String.format("SAFE_ACCESS.readObject(constants, %s)", index);
            }

            private CodeExecutableElement createAsBytecodeIndex() {
                CodeExecutableElement ex = GeneratorUtils.override(types.Instruction_Argument, "asBytecodeIndex");
                ex.getModifiers().add(Modifier.FINAL);
                CodeTreeBuilder b = ex.createBuilder();
                b.declaration(type(byte[].class), "bc", "this.bytecode.bytecodes");
                b.startReturn();

                b.string(readIntSafe("bc", "bci"));
                b.end();
                return ex;
            }

            private CodeExecutableElement createAsInteger() {
                CodeExecutableElement ex = GeneratorUtils.override(types.Instruction_Argument, "asInteger");
                ex.getModifiers().add(Modifier.FINAL);
                CodeTreeBuilder b = ex.createBuilder();
                b.declaration(type(byte[].class), "bc", "this.bytecode.bytecodes");
                b.startSwitch().string("width").end().startBlock();
                b.startCase().string("1").end();
                b.startCaseBlock().startReturn().string(readByteSafe("bc", "bci")).end(2);
                b.startCase().string("2").end();
                b.startCaseBlock().startReturn().string(readShortSafe("bc", "bci")).end(2);
                b.startCase().string("4").end();
                b.startCaseBlock().startReturn().string(readIntSafe("bc", "bci")).end(2);
                b.caseDefault().startCaseBlock();
                emitThrowAssertionError(b, "\"Unexpected integer width \" + width");
                b.end();
                b.end(); // switch
                return ex;
            }

            private CodeExecutableElement createAsLocalOffset() {
                CodeExecutableElement ex = GeneratorUtils.override(types.Instruction_Argument, "asLocalOffset");
                ex.getModifiers().add(Modifier.FINAL);
                CodeTreeBuilder b = ex.createBuilder();
                b.declaration(type(byte[].class), "bc", "this.bytecode.bytecodes");
                b.startReturn();
                if (ImmediateKind.FRAME_INDEX.width != ImmediateWidth.SHORT) {
                    throw new AssertionError("encoding changed");
                }
                b.string(readShortSafe("bc", "bci")).string(" - USER_LOCALS_START_INDEX");
                b.end();
                return ex;
            }

            private CodeExecutableElement createAsLocalIndex() {
                CodeExecutableElement ex = GeneratorUtils.override(types.Instruction_Argument, "asLocalIndex");
                ex.getModifiers().add(Modifier.FINAL);
                CodeTreeBuilder b = ex.createBuilder();
                b.declaration(type(byte[].class), "bc", "this.bytecode.bytecodes");
                b.startReturn();
                if (ImmediateKind.LOCAL_INDEX.width != ImmediateWidth.SHORT) {
                    throw new AssertionError("encoding changed");
                }
                b.string(readShortSafe("bc", "bci"));
                b.end();
                return ex;
            }

            private CodeExecutableElement createAsConstant() {
                CodeExecutableElement ex = GeneratorUtils.override(types.Instruction_Argument, "asConstant");
                ex.getModifiers().add(Modifier.FINAL);
                CodeTreeBuilder b = ex.createBuilder();
                b.declaration(type(byte[].class), "bc", "this.bytecode.bytecodes");
                b.declaration(type(Object[].class), "constants", "this.bytecode.constants");
                b.startReturn();
                if (ImmediateKind.CONSTANT.width != ImmediateWidth.INT) {
                    throw new AssertionError("encoding changed");
                }
                b.string(readConstSafe(readIntSafe("bc", "bci")));
                b.end();
                return ex;
            }

            private CodeExecutableElement createAsCachedNode() {
                CodeExecutableElement ex = GeneratorUtils.override(types.Instruction_Argument, "asCachedNode");
                ex.getModifiers().add(Modifier.FINAL);
                CodeTreeBuilder b = ex.createBuilder();
                b.declaration(arrayOf(types.Node), "cachedNodes", "this.bytecode.getCachedNodes()");
                b.startIf().string("cachedNodes == null").end().startBlock();
                b.statement("return null");
                b.end();
                b.declaration(type(byte[].class), "bc", "this.bytecode.bytecodes");
                b.startReturn();
                if (ImmediateKind.NODE_PROFILE.width != ImmediateWidth.INT) {
                    throw new AssertionError("encoding changed");
                }
                b.string("cachedNodes[", readIntSafe("bc", "bci"), "]");
                b.end();
                return ex;
            }

            private CodeExecutableElement createAsTagNode() {
                CodeExecutableElement ex = GeneratorUtils.override(types.Instruction_Argument, "asTagNode");
                ex.getModifiers().add(Modifier.FINAL);
                CodeTreeBuilder b = ex.createBuilder();
                b.declaration(type(byte[].class), "bc", "this.bytecode.bytecodes");
                b.declaration(tagRootNode.asType(), "tagRoot", "this.bytecode.tagRoot");
                b.startIf().string("tagRoot == null").end().startBlock();
                b.statement("return null");
                b.end();
                b.startReturn();
                if (ImmediateKind.TAG_NODE.width != ImmediateWidth.INT) {
                    throw new AssertionError("encoding changed");
                }
                b.tree(readTagNodeSafe(CodeTreeBuilder.singleString(readIntSafe("bc", "bci"))));
                b.end();
                return ex;
            }

            private CodeExecutableElement createAsBranchProfile() {
                CodeExecutableElement ex = GeneratorUtils.override(types.Instruction_Argument, "asBranchProfile");
                ex.getModifiers().add(Modifier.FINAL);
                CodeTreeBuilder b = ex.createBuilder();
                b.declaration(type(byte[].class), "bc", "this.bytecode.bytecodes");
                if (ImmediateKind.BRANCH_PROFILE.width != ImmediateWidth.INT) {
                    throw new AssertionError("encoding changed");
                }
                b.declaration(type(int.class), "index", readIntSafe("bc", "bci"));
                b.declaration(type(int[].class), "profiles", "this.bytecode.getBranchProfiles()");
                b.startIf().string("profiles == null").end().startBlock();

                b.startReturn();
                b.startNew(types.Instruction_Argument_BranchProfile);
                b.string("index");
                b.string("0");
                b.string("0");
                b.end(); // new
                b.end(); // return

                b.end(); // block
                b.startReturn();
                b.startNew(types.Instruction_Argument_BranchProfile);
                b.string("index");
                b.string("profiles[index * 2]");
                b.string("profiles[index * 2 + 1]");
                b.end();
                b.end();
                return ex;
            }

            private CodeExecutableElement createGetKind() {
                CodeExecutableElement ex = GeneratorUtils.override(types.Instruction_Argument, "getKind");
                ex.getModifiers().add(Modifier.FINAL);
                CodeTreeBuilder b = ex.createBuilder();
                b.startReturn();
                String name = switch (immediateKind) {
                    case BRANCH_PROFILE -> "BRANCH_PROFILE";
                    case BYTECODE_INDEX -> "BYTECODE_INDEX";
                    case CONSTANT -> "CONSTANT";
                    case FRAME_INDEX -> "LOCAL_OFFSET";
                    case LOCAL_INDEX -> "LOCAL_INDEX";
                    case SHORT, LOCAL_ROOT, STACK_POINTER -> "INTEGER";
                    case NODE_PROFILE -> "NODE_PROFILE";
                    case TAG_NODE -> "TAG_NODE";
                };
                b.staticReference(types.Instruction_Argument_Kind, name);
                b.end();
                return ex;
            }

        }

    }

    final class TagNodeElement extends CodeTypeElement {

        TagNodeElement() {
            super(Set.of(PRIVATE, STATIC, FINAL),
                            ElementKind.CLASS, null, "TagNode");
            this.setSuperClass(types.TagTreeNode);
            this.getImplements().add(types.InstrumentableNode);
            this.getImplements().add(types.TagTree);

            this.add(new CodeVariableElement(Set.of(FINAL, STATIC), arrayOf(this.asType()), "EMPTY_ARRAY")).createInitBuilder().string("new TagNode[0]");

            this.add(new CodeVariableElement(Set.of(FINAL), type(int.class), "tags"));
            this.add(new CodeVariableElement(Set.of(FINAL), type(int.class), "enterBci"));

            CodeExecutableElement constructor = this.add(createConstructorUsingFields(Set.of(), this, null));
            CodeTreeBuilder b = constructor.createBuilder();
            b.startStatement().startSuperCall().staticReference(bytecodeRootNodesImpl.asType(), "VISIBLE_TOKEN").end().end();
            b.statement("this.tags = tags");
            b.statement("this.enterBci = enterBci");

            compFinal(this.add(new CodeVariableElement(Set.of(), type(int.class), "returnBci")));
            child(this.add(new CodeVariableElement(Set.of(), arrayOf(this.asType()), "children")));

            child(this.add(new CodeVariableElement(Set.of(PRIVATE, VOLATILE), types.ProbeNode, "probe")));
            compFinal(this.add(new CodeVariableElement(Set.of(PRIVATE, VOLATILE), types.SourceSection, "sourceSection")));

        }

        void lazyInit() {
            this.add(createCreateWrapper());
            this.add(createFindProbe());
            this.add(createIsInstrumentable());
            this.add(createHasTag());
            this.add(createCopy());
            this.add(createGetSourceSection());
            this.add(createGetSourceSections());
            this.add(createCreateSourceSection());
            this.add(createFindBytecodeNode());
            this.addOptional(createDispatch());
            this.add(createGetLanguage());

            // TagTree
            this.add(createGetTreeChildren());
            this.add(createGetTags());
            this.add(createGetEnterBytecodeIndex());
            this.add(createGetReturnBytecodeIndex());
        }

        private CodeExecutableElement createGetTreeChildren() {
            CodeExecutableElement ex = GeneratorUtils.override(types.TagTree, "getTreeChildren");
            ex.getModifiers().remove(Modifier.ABSTRACT);
            ex.getModifiers().add(Modifier.FINAL);
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn().startStaticCall(type(List.class), "of").string("this.children").end().end();
            return ex;
        }

        private CodeExecutableElement createGetTags() {
            CodeExecutableElement ex = GeneratorUtils.override(types.TagTree, "getTags");
            ex.getModifiers().remove(Modifier.ABSTRACT);
            ex.getModifiers().add(Modifier.FINAL);
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn().startStaticCall(type(List.class), "of").string("mapTagMaskToTagsArray(this.tags)").end().end();
            return ex;
        }

        private CodeExecutableElement createGetEnterBytecodeIndex() {
            CodeExecutableElement ex = GeneratorUtils.override(types.TagTree, "getEnterBytecodeIndex");
            ex.getModifiers().remove(Modifier.ABSTRACT);
            ex.getModifiers().add(Modifier.FINAL);
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return this.enterBci");
            return ex;
        }

        private CodeExecutableElement createGetReturnBytecodeIndex() {
            CodeExecutableElement ex = GeneratorUtils.override(types.TagTree, "getReturnBytecodeIndex");
            ex.getModifiers().remove(Modifier.ABSTRACT);
            ex.getModifiers().add(Modifier.FINAL);
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return this.returnBci");
            return ex;
        }

        private CodeExecutableElement createCreateWrapper() {
            CodeExecutableElement ex = GeneratorUtils.override(types.InstrumentableNode, "createWrapper", new String[]{"p"});
            ex.getModifiers().remove(Modifier.ABSTRACT);
            ex.getModifiers().add(Modifier.FINAL);
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return null");
            return ex;
        }

        private CodeExecutableElement createFindProbe() {
            CodeExecutableElement ex = GeneratorUtils.override(types.InstrumentableNode, "findProbe");
            ex.getModifiers().remove(Modifier.ABSTRACT);
            ex.getModifiers().add(Modifier.FINAL);
            CodeTreeBuilder b = ex.createBuilder();
            b.declaration(types.ProbeNode, "p", "this.probe");
            b.startIf().string("p == null").end().startBlock();
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
            b.statement("this.probe = p = insert(createProbe(getSourceSection()))");
            b.end();
            b.startStatement().startStaticCall(types.CompilerAsserts, "partialEvaluationConstant").string("p").end().end();
            b.statement("return p");
            return ex;
        }

        private CodeExecutableElement createFindBytecodeNode() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), abstractBytecodeNode.asType(), "findBytecodeNode");
            CodeTreeBuilder b = ex.createBuilder();
            b.declaration(types.Node, "current", "this");
            b.startWhile().string("!(").instanceOf("current", abstractBytecodeNode.asType()).string(" bytecodeNode)").end().startBlock();
            b.statement("current = current.getParent()");
            b.end();

            b.startIf().string("bytecodeNode == null").end().startBlock();
            b.tree(GeneratorUtils.createShouldNotReachHere("Unexpected disconnected node."));
            b.end();
            b.statement("return bytecodeNode");
            return withTruffleBoundary(ex);
        }

        private CodeExecutableElement createGetLanguage() {
            CodeExecutableElement ex = GeneratorUtils.override(types.TagTreeNode, "getLanguage");
            ex.getModifiers().remove(Modifier.ABSTRACT);
            ex.getModifiers().add(Modifier.FINAL);
            ex.setReturnType(generic(type(Class.class), model.languageClass));
            ex.getAnnotationMirrors().clear();
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn().typeLiteral(model.languageClass).end();
            b.end();
            return ex;
        }

        private CodeExecutableElement createDispatch() {
            if (ElementUtils.typeEquals(model.tagTreeNodeLibrary.getTemplateType().asType(),
                            types.TagTreeNodeExports)) {
                // use default implementation
                return null;
            }

            CodeExecutableElement ex = GeneratorUtils.override(types.TagTreeNode, "dispatch");
            ex.getModifiers().remove(Modifier.ABSTRACT);
            ex.getModifiers().add(Modifier.FINAL);
            ex.getAnnotationMirrors().clear();

            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn().typeLiteral(model.tagTreeNodeLibrary.getTemplateType().asType()).end();
            return ex;
        }

        private CodeExecutableElement createGetSourceSection() {
            CodeExecutableElement ex = GeneratorUtils.override(types.Node, "getSourceSection");
            ex.getModifiers().remove(Modifier.ABSTRACT);
            ex.getModifiers().add(Modifier.FINAL);
            CodeTreeBuilder b = ex.createBuilder();

            b.declaration(types.SourceSection, "section", "this.sourceSection");
            b.startIf().string("section == null").end().startBlock();
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
            b.statement("this.sourceSection = section = createSourceSection()");
            b.end();
            b.statement("return section");
            return ex;
        }

        private CodeExecutableElement createGetSourceSections() {
            CodeExecutableElement ex = GeneratorUtils.override(types.TagTree, "getSourceSections");
            ex.getModifiers().remove(Modifier.ABSTRACT);
            ex.getModifiers().add(Modifier.FINAL);
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn().string("findBytecodeNode().getSourceLocations(enterBci)").end();
            return ex;
        }

        private CodeExecutableElement createCreateSourceSection() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), types.SourceSection, "createSourceSection");
            ex.getModifiers().remove(Modifier.ABSTRACT);
            ex.getModifiers().add(Modifier.FINAL);
            CodeTreeBuilder b = ex.createBuilder();

            b.startIf().string("enterBci == -1").end().startBlock();
            b.lineComment("only happens for synthetic instrumentable root nodes.");
            b.statement("return null");
            b.end();

            // Because of operation nesting, any source section that applies to the tag.enter should
            // apply to the whole tag operation.
            b.startReturn().string("findBytecodeNode().getSourceLocation(enterBci)").end();
            return ex;
        }

        private CodeExecutableElement createIsInstrumentable() {
            CodeExecutableElement ex = GeneratorUtils.override(types.InstrumentableNode, "isInstrumentable");
            ex.getModifiers().remove(Modifier.ABSTRACT);
            ex.getModifiers().add(Modifier.FINAL);
            ex.createBuilder().returnTrue();
            return ex;
        }

        private CodeExecutableElement createCopy() {
            CodeExecutableElement ex = GeneratorUtils.override(types.Node, "copy");
            ex.getModifiers().remove(Modifier.ABSTRACT);
            ex.getModifiers().add(Modifier.FINAL);
            CodeTreeBuilder b = ex.createBuilder();
            b.startDeclaration(asType(), "copy").cast(asType()).string("super.copy()").end();
            b.statement("copy.probe = null");
            b.statement("return copy");
            return ex;
        }

        private CodeExecutableElement createHasTag() {
            CodeExecutableElement ex = GeneratorUtils.override(types.InstrumentableNode, "hasTag", new String[]{"tag"});
            ex.getModifiers().remove(Modifier.ABSTRACT);
            ex.getModifiers().add(Modifier.FINAL);
            CodeTreeBuilder b = ex.createBuilder();

            boolean elseIf = false;
            int index = 0;
            for (TypeMirror tag : model.getProvidedTags()) {
                elseIf = b.startIf(elseIf);
                b.string("tag == ").typeLiteral(tag).end().startBlock();
                int mask = 1 << index;
                b.startReturn().string("(tags & 0x", Integer.toHexString(mask), ") != 0").end();
                b.end();
                index++;
            }
            b.returnFalse();
            return ex;
        }

    }

    final class TagRootNodeElement extends CodeTypeElement {

        TagRootNodeElement() {
            super(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "TagRootNode");
            this.setSuperClass(types.Node);
            child(this.add(new CodeVariableElement(Set.of(), tagNode.asType(), "root")));
            this.add(compFinal(1, new CodeVariableElement(Set.of(FINAL), arrayOf(tagNode.asType()), "tagNodes")));
            this.add(GeneratorUtils.createConstructorUsingFields(Set.of(), this));

            child(this.add(new CodeVariableElement(Set.of(), types.ProbeNode, "probe")));
            CodeExecutableElement getProbe = this.add(new CodeExecutableElement(Set.of(), types.ProbeNode, "getProbe"));
            CodeTreeBuilder b = getProbe.createBuilder();
            b.declaration(types.ProbeNode, "localProbe", "this.probe");
            b.startIf().string("localProbe == null").end().startBlock();
            b.statement("this.probe = localProbe = insert(root.createProbe(null))");
            b.end();
            b.statement("return localProbe");

            this.add(createCopy());
        }

        private CodeExecutableElement createCopy() {
            CodeExecutableElement ex = GeneratorUtils.override(types.Node, "copy");
            ex.getModifiers().remove(Modifier.ABSTRACT);
            ex.getModifiers().add(Modifier.FINAL);
            CodeTreeBuilder b = ex.createBuilder();
            b.startDeclaration(asType(), "copy").cast(asType()).string("super.copy()").end();
            b.statement("copy.probe = null");
            b.statement("return copy");
            return ex;
        }

    }

    final class AbstractBytecodeNodeElement extends CodeTypeElement {

        private final CodeExecutableElement continueAt;
        private final CodeExecutableElement getCachedLocalTagInternal;
        private final CodeExecutableElement setCachedLocalTagInternal;
        private final CodeExecutableElement checkStableTagsAssumption;

        AbstractBytecodeNodeElement() {
            super(Set.of(PRIVATE, STATIC, ABSTRACT, SEALED), ElementKind.CLASS, null, "AbstractBytecodeNode");

            setSuperClass(types.BytecodeNode);
            add(compFinal(1, new CodeVariableElement(Set.of(FINAL), arrayOf(type(byte.class)), "bytecodes")));
            add(compFinal(1, new CodeVariableElement(Set.of(FINAL), arrayOf(type(Object.class)), "constants")));
            add(compFinal(1, new CodeVariableElement(Set.of(FINAL), arrayOf(type(int.class)), "handlers")));
            add(compFinal(1, new CodeVariableElement(Set.of(FINAL), type(int[].class), "locals")));
            add(compFinal(1, new CodeVariableElement(Set.of(FINAL), type(int[].class), "sourceInfo")));
            add(new CodeVariableElement(Set.of(FINAL), generic(type(List.class), types.Source), "sources"));
            add(new CodeVariableElement(Set.of(FINAL), type(int.class), "numNodes"));

            if (model.enableTagInstrumentation) {
                child(add(new CodeVariableElement(Set.of(), tagRootNode.asType(), "tagRoot")));
            }

            for (ExecutableElement superConstructor : ElementFilter.constructorsIn(ElementUtils.castTypeElement(types.BytecodeNode).getEnclosedElements())) {
                CodeExecutableElement constructor = CodeExecutableElement.cloneNoAnnotations(superConstructor);
                constructor.setReturnType(null);
                constructor.setSimpleName(this.getSimpleName());
                constructor.getParameters().remove(0);

                for (VariableElement var : ElementFilter.fieldsIn(this.getEnclosedElements())) {
                    constructor.addParameter(new CodeVariableElement(var.asType(), var.getSimpleName().toString()));
                }

                CodeTreeBuilder b = constructor.createBuilder();
                b.startStatement().startSuperCall().string("BytecodeRootNodesImpl.VISIBLE_TOKEN").end().end();
                for (VariableElement var : ElementFilter.fieldsIn(this.getEnclosedElements())) {
                    b.startStatement();
                    b.string("this.", var.getSimpleName().toString(), " = ", var.getSimpleName().toString());
                    b.end();
                }
                add(constructor);
                break;
            }

            if (model.enableTagInstrumentation) {
                add(createFindInstrumentableCallNode());
            }

            add(createFindBytecodeIndex2());
            add(createReadValidBytecode());

            continueAt = add(new CodeExecutableElement(Set.of(ABSTRACT), type(long.class), "continueAt"));
            continueAt.addParameter(new CodeVariableElement(BytecodeRootNodeElement.this.asType(), "$root"));
            continueAt.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));
            if (model.enableYield) {
                continueAt.addParameter(new CodeVariableElement(types.VirtualFrame, "localFrame"));
            }
            continueAt.addParameter(new CodeVariableElement(type(long.class), "startState"));

            var getRoot = add(new CodeExecutableElement(Set.of(FINAL), BytecodeRootNodeElement.this.asType(), "getRoot"));
            CodeTreeBuilder b = getRoot.createBuilder();
            b.startReturn().cast(BytecodeRootNodeElement.this.asType()).string("getParent()").end();

            var findLocation = this.add(new CodeExecutableElement(Set.of(STATIC), types.BytecodeLocation, "findLocation"));
            findLocation.addParameter(new CodeVariableElement(this.asType(), "node"));
            findLocation.addParameter(new CodeVariableElement(type(int.class), "bci"));
            b = findLocation.createBuilder();
            b.startReturn().startCall("node.findLocation").string("bci").end().end();

            var toCached = this.add(new CodeExecutableElement(Set.of(ABSTRACT), this.asType(), "toCached"));
            if (model.usesBoxingElimination()) {
                toCached.addParameter(new CodeVariableElement(type(int.class), "numLocals"));
            }

            CodeExecutableElement update = this.add(new CodeExecutableElement(Set.of(ABSTRACT), this.asType(), "update"));

            for (VariableElement e : ElementFilter.fieldsIn(this.getEnclosedElements())) {
                update.addParameter(new CodeVariableElement(e.asType(), e.getSimpleName().toString() + "_"));
            }

            if (model.isBytecodeUpdatable()) {
                this.add(createInvalidate());
                if (model.enableYield) {
                    this.add(createUpdateContinuationRootNodes());
                }
            }

            this.add(createValidateBytecodes());
            this.add(createDumpInvalid());

            this.add(new CodeExecutableElement(Set.of(ABSTRACT), this.asType(), "cloneUninitialized"));
            this.add(new CodeExecutableElement(Set.of(ABSTRACT), arrayOf(types.Node), "getCachedNodes"));
            this.add(new CodeExecutableElement(Set.of(ABSTRACT), arrayOf(type(int.class)), "getBranchProfiles"));
            if (model.usesBoxingElimination()) {
                this.add(new CodeExecutableElement(Set.of(ABSTRACT), arrayOf(type(byte.class)), "getLocalTags"));
                /**
                 * Even though tags are only cached on cached nodes, all nodes need to implement
                 * these methods, because the callee does not know if the node is cached/uncached.
                 */
                getCachedLocalTagInternal = this.add(createGetCachedLocalTagInternal());
                setCachedLocalTagInternal = this.add(createSetCachedLocalTagInternal());
                if (model.enableYield) {
                    checkStableTagsAssumption = this.add(createCheckStableTagsAssumption());
                } else {
                    checkStableTagsAssumption = null;
                }
            } else {
                getCachedLocalTagInternal = null;
                setCachedLocalTagInternal = null;
                checkStableTagsAssumption = null;
            }

            // Define methods for introspecting the bytecode and source.
            this.add(createGetSourceSection());
            this.add(createGetSourceLocation());
            this.add(createGetSourceLocations());
            this.add(createCreateSourceSection());
            this.add(createFindInstruction());
            this.add(createValidateBytecodeIndex());
            this.add(createGetSourceInformation());
            this.add(createHasSourceInformation());
            this.add(createGetSourceInformationTree());
            this.add(createGetExceptionHandlers());
            this.add(createGetTagTree());

            this.add(createGetLocalCount());
            this.add(createClearLocalValueInternal());
            this.add(createIsLocalClearedInternal());
            this.add(createGetLocalNameInternal());
            this.add(createGetLocalInfoInternal());

            if (!model.usesBoxingElimination()) {
                this.add(createGetLocalValue());
                this.add(createSetLocalValue());
                this.add(createGetLocalValueInternal());
                this.add(createSetLocalValueInternal());
            } else {
                this.add(createAbstractSetLocalValueInternal());
            }

            if (model.enableBlockScoping) {
                this.add(createLocalOffsetToTableIndex());
                this.add(createLocalOffsetToLocalIndex());
                this.add(createLocalIndexToAnyTableIndex());
            }
            if (model.canValidateMaterializedLocalLiveness()) {
                this.add(createValidateMaterializedLocalLivenessInternal());
                this.add(createLocalIndexToTableIndex());
            }

            this.add(createGetLocalName());
            this.add(createGetLocalInfo());
            this.add(createGetLocals());

            if (model.enableTagInstrumentation) {
                this.add(createGetTagNodes());
            }

            this.add(createTranslateBytecodeIndex());
            if (model.isBytecodeUpdatable()) {
                this.add(createTransitionState());
                this.add(createToStableBytecodeIndex());
                this.add(createFromStableBytecodeIndex());
                this.add(createTransitionInstrumentationIndex());
                this.add(createComputeNewBci());
            }
            this.add(createAdoptNodesAfterUpdate());
        }

        private CodeExecutableElement createGetLocalCount() {
            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "getLocalCount", new String[]{"bci"}, new TypeMirror[]{type(int.class)});
            ex.getModifiers().add(FINAL);
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("assert validateBytecodeIndex(bci)");
            ex.getAnnotationMirrors().add(new CodeAnnotationMirror(types.ExplodeLoop));
            b.startStatement().startStaticCall(types.CompilerAsserts, "partialEvaluationConstant").string("bci").end().end();

            if (model.enableBlockScoping) {
                b.declaration(type(int.class), "count", "0");
                b.startFor().string("int index = 0; index < locals.length; index += LOCALS_LENGTH").end().startBlock();
                b.declaration(type(int.class), "startIndex", "locals[index + LOCALS_OFFSET_START_BCI]");
                b.declaration(type(int.class), "endIndex", "locals[index + LOCALS_OFFSET_END_BCI]");
                b.startIf().string("bci >= startIndex && bci < endIndex").end().startBlock();
                b.statement("count++");
                b.end();
                b.end();
                b.startStatement().startStaticCall(types.CompilerAsserts, "partialEvaluationConstant").string("count").end().end();
                b.statement("return count");
            } else {
                b.statement("return locals.length / LOCALS_LENGTH");
            }
            return ex;
        }

        private CodeExecutableElement createClearLocalValueInternal() {
            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "clearLocalValueInternal",
                            new String[]{"frame", "localOffset", "localIndex"},
                            new TypeMirror[]{types.Frame, type(int.class), type(int.class)});
            ex.getModifiers().add(FINAL);

            CodeTreeBuilder b = ex.createBuilder();
            buildVerifyFrameDescriptor(b, true);
            b.declaration(type(int.class), "frameIndex", "USER_LOCALS_START_INDEX + localOffset");
            b.statement("frame.clear(frameIndex)");

            return ex;
        }

        private CodeExecutableElement createIsLocalClearedInternal() {
            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "isLocalClearedInternal",
                            new String[]{"frame", "localOffset", "localIndex"},
                            new TypeMirror[]{types.Frame, type(int.class), type(int.class)});
            ex.getModifiers().add(FINAL);

            CodeTreeBuilder b = ex.createBuilder();
            buildVerifyFrameDescriptor(b, true);
            b.declaration(type(int.class), "frameIndex", "USER_LOCALS_START_INDEX + localOffset");
            b.startReturn();
            b.string("frame.getTag(frameIndex) == FrameSlotKind.Illegal.tag");
            b.end();

            return ex;
        }

        private CodeExecutableElement createGetLocalValue() {
            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "getLocalValue",
                            new String[]{"bci", "frame", "localOffset"},
                            new TypeMirror[]{type(int.class), types.Frame, type(int.class)});
            ex.getModifiers().add(FINAL);

            CodeTreeBuilder b = ex.createBuilder();
            b.statement("assert validateBytecodeIndex(bci)");
            buildVerifyLocalsIndex(b);
            buildVerifyFrameDescriptor(b, false);

            b.declaration(type(int.class), "frameIndex", "USER_LOCALS_START_INDEX + localOffset");
            b.startIf().string("frame.isObject(frameIndex)").end().startBlock();
            b.startReturn().string("frame.getObject(frameIndex)").end();
            b.end();
            b.statement("return null");

            return ex;
        }

        private CodeExecutableElement createGetLocalValueInternal() {
            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "getLocalValueInternal",
                            new String[]{"frame", "localOffset", "localIndex"},
                            new TypeMirror[]{types.Frame, type(int.class), type(int.class)});
            ex.getModifiers().add(FINAL);

            CodeTreeBuilder b = ex.createBuilder();
            buildVerifyFrameDescriptor(b, true);

            b.declaration(type(int.class), "frameIndex", "USER_LOCALS_START_INDEX + localOffset");
            b.startReturn().string("frame.getObject(frameIndex)").end();
            return ex;
        }

        private CodeExecutableElement createSetLocalValue() {
            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "setLocalValue",
                            new String[]{"bci", "frame", "localOffset", "value"},
                            new TypeMirror[]{type(int.class), types.Frame, type(int.class), type(Object.class)});
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("assert validateBytecodeIndex(bci)");
            AbstractBytecodeNodeElement.buildVerifyLocalsIndex(b);
            buildVerifyFrameDescriptor(b, false);
            b.declaration(type(int.class), "frameIndex", "USER_LOCALS_START_INDEX + localOffset");
            b.startStatement();
            b.startCall("frame", getSetMethod(type(Object.class))).string("frameIndex").string("value").end();
            b.end();
            return ex;
        }

        private CodeExecutableElement createSetLocalValueInternal() {
            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "setLocalValueInternal",
                            new String[]{"frame", "localOffset", "localIndex", "value"},
                            new TypeMirror[]{types.Frame, type(int.class), type(int.class), type(Object.class)});
            CodeTreeBuilder b = ex.createBuilder();
            AbstractBytecodeNodeElement.buildVerifyFrameDescriptor(b, true);

            b.startStatement();
            b.startCall("frame", getSetMethod(type(Object.class))).string("USER_LOCALS_START_INDEX + localOffset").string("value").end();
            b.end();
            return ex;
        }

        private CodeExecutableElement createAbstractSetLocalValueInternal() {
            // Redeclare the method so it is visible on the AbstractBytecodeNode.
            if (!model.usesBoxingElimination()) {
                throw new AssertionError();
            }
            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "setLocalValueInternal",
                            new String[]{"frame", "localOffset", "localIndex", "value"},
                            new TypeMirror[]{types.Frame, type(int.class), type(int.class), type(Object.class)});
            ex.getModifiers().add(ABSTRACT);
            return ex;
        }

        private CodeExecutableElement createLocalOffsetToTableIndex() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PROTECTED, FINAL), type(int.class), "localOffsetToTableIndex");
            ex.addParameter(new CodeVariableElement(type(int.class), "bci"));
            ex.addParameter(new CodeVariableElement(type(int.class), "localOffset"));
            ex.addAnnotationMirror(new CodeAnnotationMirror(types.ExplodeLoop));
            CodeTreeBuilder b = ex.createBuilder();
            b.declaration(type(int.class), "count", "0");
            b.startFor().string("int index = 0; index < locals.length; index += LOCALS_LENGTH").end().startBlock();
            b.declaration(type(int.class), "startIndex", "locals[index + LOCALS_OFFSET_START_BCI]");
            b.declaration(type(int.class), "endIndex", "locals[index + LOCALS_OFFSET_END_BCI]");
            b.startIf().string("bci >= startIndex && bci < endIndex").end().startBlock();
            b.startIf().string("count == localOffset").end().startBlock();
            b.startReturn().string("index").end();
            b.end();
            b.statement("count++");
            b.end();
            b.end();
            b.statement("return -1");
            return ex;
        }

        private CodeExecutableElement createLocalOffsetToLocalIndex() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PROTECTED, FINAL), type(int.class), "localOffsetToLocalIndex");
            ex.addParameter(new CodeVariableElement(type(int.class), "bci"));
            ex.addParameter(new CodeVariableElement(type(int.class), "localOffset"));
            CodeTreeBuilder b = ex.createBuilder();
            b.declaration(type(int.class), "tableIndex", "localOffsetToTableIndex(bci, localOffset)");
            b.startAssert().string("locals[tableIndex + LOCALS_OFFSET_FRAME_INDEX] == localOffset + USER_LOCALS_START_INDEX : ").doubleQuote("Inconsistent indices.").end();
            b.startReturn().string("locals[tableIndex + LOCALS_OFFSET_LOCAL_INDEX]").end();
            return ex;
        }

        private CodeExecutableElement createLocalIndexToTableIndex() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PROTECTED, FINAL), type(int.class), "localIndexToTableIndex");
            ex.addParameter(new CodeVariableElement(type(int.class), "bci"));
            ex.addParameter(new CodeVariableElement(type(int.class), "localIndex"));
            ex.addAnnotationMirror(new CodeAnnotationMirror(types.ExplodeLoop));
            CodeTreeBuilder b = ex.createBuilder();
            b.startFor().string("int index = 0; index < locals.length; index += LOCALS_LENGTH").end().startBlock();
            b.declaration(type(int.class), "startIndex", "locals[index + LOCALS_OFFSET_START_BCI]");
            b.declaration(type(int.class), "endIndex", "locals[index + LOCALS_OFFSET_END_BCI]");
            b.startIf().string("bci >= startIndex && bci < endIndex").end().startBlock();
            b.startIf().string("locals[index + LOCALS_OFFSET_LOCAL_INDEX] == localIndex").end().startBlock();
            b.startReturn().string("index").end();
            b.end();
            b.end();
            b.end();
            b.statement("return -1");
            return ex;
        }

        /**
         * Like localIndexToTableIndex, but does not check a bci. Useful for metadata (names/infos)
         * that are consistent across all table entries for a given local index.
         */
        private CodeExecutableElement createLocalIndexToAnyTableIndex() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PROTECTED, FINAL), type(int.class), "localIndexToAnyTableIndex");
            ex.addParameter(new CodeVariableElement(type(int.class), "localIndex"));
            ex.addAnnotationMirror(new CodeAnnotationMirror(types.ExplodeLoop));
            CodeTreeBuilder b = ex.createBuilder();
            b.startFor().string("int index = 0; index < locals.length; index += LOCALS_LENGTH").end().startBlock();
            b.startIf().string("locals[index + LOCALS_OFFSET_LOCAL_INDEX] == localIndex").end().startBlock();
            b.startReturn().string("index").end();
            b.end();
            b.end();
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
            b.startThrow().startNew(type(AssertionError.class));
            b.doubleQuote("Local index not found in locals table");
            b.end(2);
            return ex;
        }

        private CodeExecutableElement createValidateMaterializedLocalLivenessInternal() {
            if (!model.canValidateMaterializedLocalLiveness()) {
                throw new AssertionError("Not supported.");
            }

            CodeExecutableElement ex = new CodeExecutableElement(Set.of(FINAL), type(boolean.class), "validateLocalLivenessInternal");
            ex.addParameter(new CodeVariableElement(types.Frame, "frame"));
            ex.addParameter(new CodeVariableElement(type(int.class), "frameIndex"));
            ex.addParameter(new CodeVariableElement(type(int.class), "localIndex"));
            ex.addParameter(new CodeVariableElement(types.Frame, "stackFrame"));
            ex.addParameter(new CodeVariableElement(type(int.class), "stackFrameBci"));

            CodeTreeBuilder b = ex.createBuilder();

            b.declaration(type(int.class), "bci");
            b.startIf().string("frame == stackFrame").end().startBlock();
            b.lineComment("Loading a value from the current frame. Use the precise bci (the frame is only updated when control escapes).");
            b.statement("bci = stackFrameBci");
            b.end();
            b.end().startElseBlock();
            b.startAssign("bci");
            startGetFrame(b, "frame", type(int.class), false).string("BCI_INDEX").end();
            b.end();
            b.end();

            b.lineComment("Ensure the local we're trying to access is live at the current bci.");
            b.startIf().string("locals[localIndexToTableIndex(bci, localIndex) + LOCALS_OFFSET_FRAME_INDEX] != frameIndex").end().startBlock();
            emitThrowIllegalArgumentException(b, "Local is out of scope in the frame passed for a materialized local access.");
            b.end();

            b.returnTrue();

            return ex;
        }

        private CodeExecutableElement createGetCachedLocalTagInternal() {
            if (!model.usesBoxingElimination()) {
                throw new AssertionError("Not supported.");
            }
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(ABSTRACT), type(byte.class), "getCachedLocalTagInternal");
            ex.addParameter(new CodeVariableElement(arrayOf(type(byte.class)), "localTags"));
            ex.addParameter(new CodeVariableElement(type(int.class), "localIndex"));
            return ex;
        }

        private CodeExecutableElement createSetCachedLocalTagInternal() {
            if (!model.usesBoxingElimination()) {
                throw new AssertionError("Not supported.");
            }
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(ABSTRACT), type(void.class), "setCachedLocalTagInternal");
            ex.addParameter(new CodeVariableElement(arrayOf(type(byte.class)), "localTags"));
            ex.addParameter(new CodeVariableElement(type(int.class), "localIndex"));
            ex.addParameter(new CodeVariableElement(type(byte.class), "tag"));
            return ex;
        }

        private CodeExecutableElement createCheckStableTagsAssumption() {
            if (!model.usesBoxingElimination()) {
                throw new AssertionError("Not supported.");
            }
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(ABSTRACT), type(boolean.class), "checkStableTagsAssumption");
            return ex;
        }

        static void buildVerifyLocalsIndex(CodeTreeBuilder b) {
            b.startStatement().startStaticCall(ProcessorContext.types().CompilerAsserts, "partialEvaluationConstant").string("bci").end().end();
            b.startStatement().startStaticCall(ProcessorContext.types().CompilerAsserts, "partialEvaluationConstant").string("localOffset").end().end();
            b.startAssert().string("localOffset >= 0 && localOffset < getLocalCount(bci) : ").doubleQuote("Invalid out-of-bounds local offset provided.").end();
        }

        static void buildVerifyFrameDescriptor(CodeTreeBuilder b, boolean trustFrame) {
            String errorMessage = "Invalid frame with invalid descriptor passed.";
            if (trustFrame) {
                b.startAssert();
                b.string("getRoot().getFrameDescriptor() == frame.getFrameDescriptor() : \"", errorMessage, "\"");
                b.end();
            } else {
                b.startIf().string("getRoot().getFrameDescriptor() != frame.getFrameDescriptor()").end().startBlock();
                emitThrowIllegalArgumentException(b, errorMessage);
                b.end();
            }
        }

        private CodeExecutableElement createGetLocalName() {
            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "getLocalName",
                            new String[]{"bci", "localOffset"}, new TypeMirror[]{type(int.class), type(int.class)});
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("assert validateBytecodeIndex(bci)");
            buildVerifyLocalsIndex(b);

            if (model.enableBlockScoping) {
                b.declaration(type(int.class), "index", "localOffsetToTableIndex(bci, localOffset)");
                b.startIf().string("index == -1").end().startBlock();
                b.returnNull();
                b.end();
                b.declaration(type(int.class), "nameId", "locals[index + LOCALS_OFFSET_NAME]");
            } else {
                b.declaration(type(int.class), "nameId", "locals[(localOffset * LOCALS_LENGTH) + LOCALS_OFFSET_NAME]");
            }
            b.startIf().string("nameId == -1").end().startBlock();
            b.returnNull();
            b.end().startElseBlock();
            b.startReturn().tree(readConst("nameId", "this.constants")).end();
            b.end();

            return ex;
        }

        private CodeExecutableElement createGetLocalNameInternal() {
            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "getLocalNameInternal",
                            new String[]{"localOffset", "localIndex"}, new TypeMirror[]{type(int.class), type(int.class)});
            CodeTreeBuilder b = ex.createBuilder();

            if (model.enableBlockScoping) {
                b.declaration(type(int.class), "index", "localIndexToAnyTableIndex(localIndex)");
                b.declaration(type(int.class), "nameId", "locals[index + LOCALS_OFFSET_NAME]");
            } else {
                b.declaration(type(int.class), "nameId", "locals[(localOffset * LOCALS_LENGTH) + LOCALS_OFFSET_NAME]");
            }
            b.startIf().string("nameId == -1").end().startBlock();
            b.returnNull();
            b.end().startElseBlock();
            b.startReturn().tree(readConst("nameId", "this.constants")).end();
            b.end();

            return ex;
        }

        private CodeExecutableElement createGetLocalInfo() {
            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "getLocalInfo",
                            new String[]{"bci", "localOffset"}, new TypeMirror[]{type(int.class), type(int.class)});
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("assert validateBytecodeIndex(bci)");
            buildVerifyLocalsIndex(b);

            if (model.enableBlockScoping) {
                b.declaration(type(int.class), "index", "localOffsetToTableIndex(bci, localOffset)");
                b.startIf().string("index == -1").end().startBlock();
                b.returnNull();
                b.end();
                b.declaration(type(int.class), "infoId", "locals[index + LOCALS_OFFSET_INFO]");
            } else {
                b.declaration(type(int.class), "infoId", "locals[(localOffset * LOCALS_LENGTH) + LOCALS_OFFSET_INFO]");
            }
            b.startIf().string("infoId == -1").end().startBlock();
            b.returnNull();
            b.end().startElseBlock();
            b.startReturn().tree(readConst("infoId", "this.constants")).end();
            b.end();

            return ex;
        }

        private CodeExecutableElement createGetLocalInfoInternal() {
            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "getLocalInfoInternal",
                            new String[]{"localOffset", "localIndex"}, new TypeMirror[]{type(int.class), type(int.class)});
            CodeTreeBuilder b = ex.createBuilder();

            if (model.enableBlockScoping) {
                b.declaration(type(int.class), "index", "localIndexToAnyTableIndex(localIndex)");
                b.declaration(type(int.class), "infoId", "locals[index + LOCALS_OFFSET_INFO]");
            } else {
                b.declaration(type(int.class), "infoId", "locals[(localOffset * LOCALS_LENGTH) + LOCALS_OFFSET_INFO]");
            }
            b.startIf().string("infoId == -1").end().startBlock();
            b.returnNull();
            b.end().startElseBlock();
            b.startReturn().tree(readConst("infoId", "this.constants")).end();
            b.end();

            return ex;
        }

        private CodeExecutableElement createGetLocals() {
            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "getLocals");
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn().startNew("LocalVariableList").string("this").end().end();
            return ex;
        }

        record InstructionValidationGroup(List<InstructionImmediate> immediates, int instructionLength, boolean allowNegativeChildBci, boolean localVar, boolean localVarMat) {

            InstructionValidationGroup(BytecodeDSLModel model, InstructionModel instruction) {
                this(instruction.getImmediates(), instruction.getInstructionLength(), acceptsInvalidChildBci(model, instruction),
                                instruction.kind.isLocalVariableAccess(),
                                instruction.kind.isLocalVariableMaterializedAccess());
            }

        }

        private CodeExecutableElement createValidateBytecodes() {
            CodeExecutableElement validate = new CodeExecutableElement(Set.of(PRIVATE, FINAL), type(boolean.class), "validateBytecodes");
            CodeTreeBuilder b = validate.createBuilder();

            b.declaration(BytecodeRootNodeElement.this.asType(), "root");
            b.declaration(arrayOf(type(byte.class)), "bc", "this.bytecodes");
            b.startIf().string("bc == null").end().startBlock();
            b.lineComment("bc is null for serialization root nodes.");
            b.statement("return true");
            b.end();

            b.declaration(arrayOf(types.Node), "cachedNodes", "getCachedNodes()");
            b.declaration(arrayOf(type(int.class)), "branchProfiles", "getBranchProfiles()");
            b.declaration(type(int.class), "bci", "0");
            if (model.enableTagInstrumentation) {
                b.declaration(arrayOf(tagNode.asType()), "tagNodes", "tagRoot != null ? tagRoot.tagNodes : null");
            }

            b.startIf().string("bc.length == 0").end().startBlock();
            b.tree(createValidationError("bytecode array must not be null"));
            b.end();

            // Bytecode validation
            b.startWhile().string("bci < bc.length").end().startBlock();
            b.startTryBlock();
            b.startSwitch().tree(readInstruction("bc", "bci")).end().startBlock();

            Map<InstructionValidationGroup, List<InstructionModel>> groups = model.getInstructions().stream().collect(
                            deterministicGroupingBy((i) -> new InstructionValidationGroup(model, i)));

            for (var entry : groups.entrySet()) {
                InstructionValidationGroup group = entry.getKey();
                List<InstructionModel> instructions = entry.getValue();

                for (InstructionModel instruction : instructions) {
                    b.startCase().tree(createInstructionConstant(instruction)).end();
                }
                b.startBlock();

                boolean rootNodeAvailable = false;
                for (InstructionImmediate immediate : group.immediates()) {
                    String localName = immediate.name();
                    CodeTree declareImmediate = CodeTreeBuilder.createBuilder() //
                                    .startDeclaration(immediate.kind().toType(context), localName) //
                                    .tree(readImmediate("bc", "bci", immediate)) //
                                    .end() //
                                    .build();

                    switch (immediate.kind()) {
                        case BYTECODE_INDEX:
                            b.tree(declareImmediate);
                            b.startIf();
                            if (group.allowNegativeChildBci()) {
                                // supports -1 immediates
                                b.string(localName, " < -1");
                            } else {
                                b.string(localName, " < 0");
                            }
                            b.string(" || ").string(localName).string(" >= bc.length").end().startBlock();
                            b.tree(createValidationErrorWithBci("bytecode index is out of bounds"));
                            b.end();
                            break;
                        case SHORT:
                            break;
                        case STACK_POINTER:
                            b.tree(declareImmediate);
                            b.startAssign("root").string("this.getRoot()").end();
                            b.declaration(type(int.class), "maxStackHeight", "root.getFrameDescriptor().getNumberOfSlots() - root.maxLocals");
                            b.startIf().string(localName, " < 0 || ", localName, " > maxStackHeight").end().startBlock();
                            b.tree(createValidationErrorWithBci("stack pointer is out of bounds"));
                            b.end();
                            break;
                        case FRAME_INDEX: {
                            b.tree(declareImmediate);
                            if (!rootNodeAvailable) {
                                rootNodeAvailable = tryEmitRootNodeForLocalInstruction(b, group);
                            }
                            b.startIf().string(localName).string(" < USER_LOCALS_START_INDEX");
                            if (rootNodeAvailable) {
                                b.string(" || ").string(localName).string(" >= root.maxLocals");
                            }
                            b.end().startBlock();
                            b.tree(createValidationErrorWithBci("local offset is out of bounds"));
                            b.end();
                            break;
                        }
                        case LOCAL_INDEX: {
                            b.tree(declareImmediate);
                            /*
                             * NB: There is an edge case where instructions have local index
                             * immediates that cannot be validated because the numLocals field is
                             * not generated (intentionally, to reduce footprint). It happens with
                             * materialized loads/stores, and only when the bci is stored in the
                             * frame, in which case the local index is validated at run time with an
                             * assertion anyway.
                             */
                            boolean hasNumLocals = model.usesBoxingElimination();
                            if (!rootNodeAvailable && hasNumLocals) {
                                rootNodeAvailable = tryEmitRootNodeForLocalInstruction(b, group);
                            }
                            b.startIf().string(localName).string(" < 0");
                            if (rootNodeAvailable && hasNumLocals) {
                                b.string(" || ").string(localName).string(" >= root.numLocals");
                            }
                            b.end().startBlock();
                            b.tree(createValidationErrorWithBci("local index is out of bounds"));
                            b.end();
                            break;
                        }
                        case LOCAL_ROOT:
                            // checked via LOCAL_OFFSET and LOCAL_INDEX
                            break;
                        case CONSTANT:
                            b.tree(declareImmediate);
                            b.startIf().string(localName).string(" < 0 || ").string(localName).string(" >= constants.length").end().startBlock();
                            b.tree(createValidationErrorWithBci("constant is out of bounds"));
                            b.end();
                            break;
                        case NODE_PROFILE:
                            b.tree(declareImmediate);
                            b.startIf().string(localName).string(" < 0 || ").string(localName).string(" >= numNodes").end().startBlock();
                            b.tree(createValidationErrorWithBci("node profile is out of bounds"));
                            b.end();
                            break;
                        case BRANCH_PROFILE:
                            b.tree(declareImmediate);
                            b.startIf().string("branchProfiles != null").end().startBlock();
                            b.startIf().string(localName).string(" < 0 || ").string(localName).string(" >= branchProfiles.length").end().startBlock();
                            b.tree(createValidationErrorWithBci("branch profile is out of bounds"));
                            b.end();
                            b.end();
                            break;
                        case TAG_NODE:
                            b.tree(declareImmediate);
                            b.startIf().string("tagNodes != null").end().startBlock();
                            b.declaration(tagNode.asType(), "node", readTagNodeSafe(CodeTreeBuilder.singleString(immediate.name())));
                            b.startIf().string("node == null").end().startBlock();
                            b.tree(createValidationErrorWithBci("tagNode is null"));
                            b.end();
                            b.end();
                            break;
                        default:
                            throw new AssertionError("Unexpected kind");
                    }
                }

                b.statement("bci = bci + " + group.instructionLength());
                b.statement("break");

                b.end();
            }

            b.caseDefault().startCaseBlock();
            b.tree(GeneratorUtils.createShouldNotReachHere(b.create().doubleQuote("Invalid BCI at index: ").string(" + bci").build()));
            b.end();

            b.end(); // switch
            b.end().startCatchBlock(type(AssertionError.class), "e");
            b.statement("throw e");
            b.end();
            b.startCatchBlock(type(Throwable.class), "e");
            b.tree(createValidationError(null, "e", false));
            b.end();

            b.end(); // while

            // Exception handler validation
            b.declaration(arrayOf(type(int.class)), "ex", "this.handlers");

            b.startIf().string("ex.length % EXCEPTION_HANDLER_LENGTH != 0").end().startBlock();
            b.tree(createValidationError("exception handler table size is incorrect"));
            b.end();

            b.startFor().string("int i = 0; i < ex.length; i = i + EXCEPTION_HANDLER_LENGTH").end().startBlock();
            b.declaration(type(int.class), "startBci", "ex[i + EXCEPTION_HANDLER_OFFSET_START_BCI]");
            b.declaration(type(int.class), "endBci", "ex[i + EXCEPTION_HANDLER_OFFSET_END_BCI]");
            b.declaration(type(int.class), "handlerKind", "ex[i + EXCEPTION_HANDLER_OFFSET_KIND]");
            b.declaration(type(int.class), "handlerBci", "ex[i + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI]");
            b.declaration(type(int.class), "handlerSp", "ex[i + EXCEPTION_HANDLER_OFFSET_HANDLER_SP]");

            b.startIf().string("startBci").string(" < 0 || ").string("startBci").string(" >= bc.length").end().startBlock();
            b.tree(createValidationError("exception handler startBci is out of bounds"));
            b.end();

            // exclusive
            b.startIf().string("endBci").string(" < 0 || ").string("endBci").string(" > bc.length").end().startBlock();
            b.tree(createValidationError("exception handler endBci is out of bounds"));
            b.end();

            b.startIf().string("startBci > endBci").end().startBlock();
            b.tree(createValidationError("exception handler bci range is malformed"));
            b.end();

            b.startSwitch().string("handlerKind").end().startBlock();
            if (model.epilogExceptional != null) {
                b.startCase().string("HANDLER_EPILOG_EXCEPTIONAL").end().startCaseBlock();

                b.startIf().string("handlerBci").string(" != -1").end().startBlock();
                b.tree(createValidationError("exception handler handlerBci is invalid"));
                b.end();

                b.startIf().string("handlerSp").string(" != -1").end().startBlock();
                b.tree(createValidationError("exception handler handlerBci is invalid"));
                b.end();

                b.statement("break");
                b.end();
            }

            if (model.enableTagInstrumentation) {
                b.startCase().string("HANDLER_TAG_EXCEPTIONAL").end().startCaseBlock();

                b.startIf().string("tagNodes != null").end().startBlock();
                b.declaration(tagNode.asType(), "node", readTagNodeSafe(CodeTreeBuilder.singleString("handlerBci")));
                b.startIf().string("node == null").end().startBlock();
                b.tree(createValidationError("tagNode is null"));
                b.end();
                b.end();

                b.statement("break");
                b.end();
            }

            b.caseDefault().startCaseBlock();
            b.startIf().string("handlerKind != HANDLER_CUSTOM").end().startBlock();
            b.tree(createValidationError("unexpected handler kind"));
            b.end();

            b.startIf().string("handlerBci").string(" < 0 || ").string("handlerBci").string(" >= bc.length").end().startBlock();
            b.tree(createValidationError("exception handler handlerBci is out of bounds"));
            b.end();

            b.statement("break");
            b.end(); // case default

            b.end(); // switch
            b.end(); // for handler

            // Source information validation
            b.declaration(arrayOf(type(int.class)), "info", "this.sourceInfo");
            b.declaration(generic(declaredType(List.class), types.Source), "localSources", "this.sources");

            b.startIf().string("info != null").end().startBlock();
            b.startFor().string("int i = 0; i < info.length; i += SOURCE_INFO_LENGTH").end().startBlock();
            b.declaration(type(int.class), "startBci", "info[i + SOURCE_INFO_OFFSET_START_BCI]");
            b.declaration(type(int.class), "endBci", "info[i + SOURCE_INFO_OFFSET_END_BCI]");
            b.declaration(type(int.class), "sourceIndex", "info[i + SOURCE_INFO_OFFSET_SOURCE]");
            b.startIf().string("startBci > endBci").end().startBlock();
            b.tree(createValidationError("source bci range is malformed"));
            b.end().startElseIf().string("sourceIndex < 0 || sourceIndex > localSources.size()").end().startBlock();
            b.tree(createValidationError("source index is out of bounds"));
            b.end();

            b.end(); // for sources
            b.end(); // if sources

            b.startReturn().string("true").end();

            return validate;
        }

        private boolean tryEmitRootNodeForLocalInstruction(CodeTreeBuilder b, InstructionValidationGroup group) {
            if (group.localVar()) {
                b.startAssign("root").string("this.getRoot()").end();
                return true;
            } else if (group.localVarMat()) {
                InstructionImmediate rootImmediate = group.immediates.stream() //
                                .filter(imm -> imm.kind() == ImmediateKind.LOCAL_ROOT) //
                                .findFirst().get();
                b.startAssign("root").startCall("this.getRoot().getBytecodeRootNodeImpl").tree(readImmediate("bc", "bci", rootImmediate)).end(2);
                return true;
            }
            return false;
        }

        /**
         * Returns true if the instruction can take -1 as a child bci.
         */
        private static boolean acceptsInvalidChildBci(BytecodeDSLModel model, InstructionModel instr) {
            if (!model.usesBoxingElimination()) {
                // Child bci immediates are only used for boxing elimination.
                return false;
            }

            if (instr.isShortCircuitConverter() || instr.isEpilogReturn()) {
                return true;
            }
            return isSameOrGenericQuickening(instr, model.popInstruction) //
                            || isSameOrGenericQuickening(instr, model.storeLocalInstruction) //
                            || (model.usesBoxingElimination() && isSameOrGenericQuickening(instr, model.conditionalOperation.instruction));
        }

        private static boolean isSameOrGenericQuickening(InstructionModel instr, InstructionModel expected) {
            return instr == expected || instr.getQuickeningRoot() == expected && instr.specializedType == null;
        }

        // calls dump, but catches any exceptions and falls back on an error string
        private CodeExecutableElement createDumpInvalid() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, FINAL), type(String.class), "dumpInvalid");
            ex.addParameter(new CodeVariableElement(types.BytecodeLocation, "highlightedLocation"));
            CodeTreeBuilder b = ex.createBuilder();

            b.startTryBlock();

            b.startReturn();
            b.string("dump(highlightedLocation)");
            b.end();

            b.end().startCatchBlock(context.getDeclaredType(Throwable.class), "t");
            b.startReturn();
            b.doubleQuote("<dump error>");
            b.end();

            b.end();

            return ex;
        }

        private CodeTree createValidationError(String message) {
            return createValidationError(message, null, false);
        }

        private CodeTree createValidationErrorWithBci(String message) {
            return createValidationError(message, null, true);
        }

        private CodeTree createValidationError(String message, String cause, boolean includeBci) {
            CodeTreeBuilder b = new CodeTreeBuilder(null);
            b.startThrow().startStaticCall(types.CompilerDirectives, "shouldNotReachHere");
            b.startGroup();
            b.startStaticCall(type(String.class), "format");
            b.startGroup().string("\"Bytecode validation error");
            if (includeBci) {
                b.string(" at index: %s.");
            } else {
                b.string(":");
            }
            if (message != null) {
                b.string(" " + message);
            }
            b.string("%n%s\"").end(); // group
            if (includeBci) {
                b.string("bci");
            }
            b.string("dumpInvalid(findLocation(bci))");
            b.end(); // String.format
            b.end(); // group
            if (cause != null) {
                b.string(cause);
            }
            b.end().end();
            return b.build();
        }

        private CodeExecutableElement createFindInstrumentableCallNode() {
            CodeExecutableElement ex = new CodeExecutableElement(
                            Set.of(FINAL),
                            types.Node, "findInstrumentableCallNode",
                            new CodeVariableElement(type(int.class), "bci"));
            CodeTreeBuilder b = ex.createBuilder();

            b.declaration(type(int[].class), "localHandlers", "handlers");
            b.startFor().string("int i = 0; i < localHandlers.length; i += EXCEPTION_HANDLER_LENGTH").end().startBlock();
            b.startIf().string("localHandlers[i + EXCEPTION_HANDLER_OFFSET_START_BCI] > bci").end().startBlock().statement("continue").end();
            b.startIf().string("localHandlers[i + EXCEPTION_HANDLER_OFFSET_END_BCI] <= bci").end().startBlock().statement("continue").end();
            b.statement("int handlerKind = localHandlers[i + EXCEPTION_HANDLER_OFFSET_KIND]");
            b.startIf().string("handlerKind != HANDLER_TAG_EXCEPTIONAL").end().startBlock();
            b.statement("continue");
            b.end();
            b.statement("int nodeId = localHandlers[i + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI]");
            b.statement("return tagRoot.tagNodes[nodeId]");
            b.end();
            b.statement("return null");
            return ex;
        }

        private CodeExecutableElement createFindBytecodeIndex2() {
            // override to make method visible
            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "findBytecodeIndex",
                            new String[]{"frame", "operationNode"}, new TypeMirror[]{types.Frame, types.Node});
            ex.getModifiers().add(ABSTRACT);
            return ex;
        }

        private CodeExecutableElement createReadValidBytecode() {
            CodeExecutableElement method = new CodeExecutableElement(
                            Set.of(FINAL),
                            type(int.class), "readValidBytecode",
                            new CodeVariableElement(type(byte[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"));
            CodeTreeBuilder b = method.createBuilder();
            if (model.isBytecodeUpdatable()) {
                b.declaration(type(int.class), "op", readInstruction("bc", "bci"));
                b.startSwitch().string("op").end().startBlock();
                for (InstructionModel instruction : model.getInvalidateInstructions()) {
                    b.startCase().tree(createInstructionConstant(instruction)).end();
                }
                b.startCaseBlock();
                b.lineComment("While we were processing the exception handler the code invalidated.");
                b.lineComment("We need to re-read the op from the old bytecodes.");
                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                b.startReturn().tree(readInstruction("oldBytecodes", "bci")).end();
                b.end(); // case
                b.caseDefault().startCaseBlock();
                b.statement("return op");
                b.end();
                b.end(); // switch
            } else {
                b.lineComment("The bytecode is not updatable so the bytecode is always valid.");
                b.startReturn().tree(readInstruction("bc", "bci")).end();
            }
            return method;
        }

        private CodeExecutableElement createGetTagNodes() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), arrayOf(tagNode.asType()), "getTagNodes");
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn().string("tagRoot != null ? tagRoot.tagNodes : null").end();
            return ex;
        }

        private CodeExecutableElement createTranslateBytecodeIndex() {
            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "translateBytecodeIndex", new String[]{"newNode", "bytecodeIndex"});
            CodeTreeBuilder b = ex.createBuilder();
            if (model.isBytecodeUpdatable()) {

                CodeTreeBuilder tb = CodeTreeBuilder.createBuilder();
                tb.startCall("transitionState");
                tb.startGroup();
                tb.cast(this.asType());
                tb.string("newNode");
                tb.end();
                tb.string(encodeState("bytecodeIndex", null));
                if (model.enableYield) {
                    tb.string("null");
                }
                tb.end();

                b.startReturn();
                b.string(decodeBci(tb.build().toString()));
                b.end();
            } else {
                b.statement("return bytecodeIndex");
            }
            return ex;
        }

        private CodeExecutableElement createTransitionState() {
            CodeExecutableElement transitionState = new CodeExecutableElement(Set.of(FINAL), type(long.class), "transitionState");
            transitionState.addParameter(new CodeVariableElement(this.asType(), "newBytecode"));
            transitionState.addParameter(new CodeVariableElement(type(long.class), "state"));
            if (model.enableYield) {
                transitionState.addParameter(new CodeVariableElement(continuationRootNodeImpl.asType(), "continuationRootNode"));
            }

            CodeTreeBuilder b = transitionState.createBuilder();

            b.declaration(arrayOf(type(byte.class)), "oldBc", "this.oldBytecodes");
            b.declaration(arrayOf(type(byte.class)), "newBc", "newBytecode.bytecodes");

            if (model.enableYield) {
                /**
                 * We can be here for one of two reasons:
                 *
                 * 1. We transitioned from uncached/uninitialized to cached. In this case, we update
                 * the ContinuationRootNode so future calls will start executing the cached
                 * interpreter.
                 *
                 * 2. Bytecode was rewritten. In this case, since the bytecode invalidation logic
                 * patches all ContinuationRootNodes with the new bytecode, we don't have to update
                 * anything.
                 */
                b.startIf().string("continuationRootNode != null && oldBc == null").end().startBlock();
                b.lineComment("Transition continuationRootNode to cached.");

                b.startDeclaration(types.BytecodeLocation, "newContinuationLocation");
                b.startCall("newBytecode.getBytecodeLocation");
                b.string("continuationRootNode.getLocation().getBytecodeIndex()");
                b.end(2);

                b.startStatement().startCall("continuationRootNode.updateBytecodeLocation");
                b.string("newContinuationLocation");
                b.string("this");
                b.string("newBytecode");
                b.doubleQuote("transition to cached");
                b.end(2);

                b.end();
            }

            b.startIf().string("oldBc == null || this == newBytecode || this.bytecodes == newBc").end().startBlock();
            b.lineComment("No change in bytecodes.");
            b.startReturn().string("state").end();
            b.end();

            b.declaration(type(int.class), "oldBci", decodeBci("state"));

            b.startDeclaration(type(int.class), "newBci");
            b.startCall("computeNewBci").string("oldBci").string("oldBc").string("newBc");
            if (model.enableTagInstrumentation) {
                b.string("this.getTagNodes()");
                b.string("newBytecode.getTagNodes()");
            }
            b.end(); // call

            b.end();

            if (model.overridesBytecodeDebugListenerMethod("onBytecodeStackTransition")) {
                b.startStatement();
                b.startCall("getRoot().onBytecodeStackTransition");
                emitParseInstruction(b, "this", "oldBci", readInstruction("oldBc", "oldBci"));
                emitParseInstruction(b, "newBytecode", "newBci", readInstruction("newBc", "newBci"));
                b.end().end();
            }

            b.startReturn().string(encodeNewBci("newBci", "state")).end();

            return transitionState;
        }

        private CodeExecutableElement createTransitionInstrumentationIndex() {
            record InstructionGroup(int instructionLength, boolean instrumentation, boolean tagInstrumentation, InstructionImmediate tagNodeImmediate) implements Comparable<InstructionGroup> {
                InstructionGroup(InstructionModel instr) {
                    this(instr.getInstructionLength(), instr.isInstrumentation(), instr.isTagInstrumentation(),
                                    instr.isTagInstrumentation() ? instr.getImmediate(ImmediateKind.TAG_NODE) : null);
                }

                // needs a deterministic ordering after grouping
                @Override
                public int compareTo(InstructionGroup o) {
                    int compare = Boolean.compare(this.instrumentation, o.instrumentation);
                    if (compare != 0) {
                        return compare;
                    }
                    compare = Boolean.compare(this.tagInstrumentation, o.tagInstrumentation);
                    if (compare != 0) {
                        return compare;
                    }

                    if (this.tagInstrumentation) {
                        compare = Integer.compare(this.tagNodeImmediate.offset(), o.tagNodeImmediate.offset());

                        if (compare != 0) {
                            return compare;
                        }
                    }

                    compare = Integer.compare(this.instructionLength, o.instructionLength);
                    if (compare != 0) {
                        return compare;
                    }
                    return 0;
                }
            }

            CodeExecutableElement invalidate = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(int.class), "transitionInstrumentationIndex");
            invalidate.addParameter(new CodeVariableElement(arrayOf(type(byte.class)), "oldBc"));
            invalidate.addParameter(new CodeVariableElement(type(int.class), "oldBciBase"));
            invalidate.addParameter(new CodeVariableElement(type(int.class), "oldBciTarget"));
            invalidate.addParameter(new CodeVariableElement(arrayOf(type(byte.class)), "newBc"));
            invalidate.addParameter(new CodeVariableElement(type(int.class), "newBciBase"));
            if (model.enableTagInstrumentation) {
                invalidate.addParameter(new CodeVariableElement(arrayOf(tagNode.asType()), "oldTagNodes"));
                invalidate.addParameter(new CodeVariableElement(arrayOf(tagNode.asType()), "newTagNodes"));
            }
            CodeTreeBuilder b = invalidate.createBuilder();
            b.declaration(type(int.class), "oldBci", "oldBciBase");
            b.declaration(type(int.class), "newBci", "newBciBase");
            b.declaration(type(short.class), "searchOp", "-1");
            if (model.enableTagInstrumentation) {
                b.declaration(type(int.class), "searchTags", "-1");
            }

            b.startWhile().string("oldBci < oldBciTarget").end().startBlock();
            b.declaration(type(short.class), "op", readInstruction("oldBc", "oldBci"));
            b.statement("searchOp = op");
            b.startSwitch().string("op").end().startBlock();
            for (var groupEntry : groupInstructionsSortedBy(InstructionGroup::new)) {
                InstructionGroup group = groupEntry.getKey();
                if (!group.instrumentation) {
                    // seeing an instrumentation here is a failure
                    continue;
                }
                List<InstructionModel> instructions = groupEntry.getValue();
                for (InstructionModel instruction : instructions) {
                    b.startCase().tree(createInstructionConstant(instruction)).end();
                }
                b.startCaseBlock();
                if (model.enableTagInstrumentation) {
                    if (group.tagInstrumentation) {
                        b.startStatement();
                        b.string("searchTags = ");
                        b.tree(readTagNode(tagNode.asType(), "oldTagNodes", readImmediate("oldBc", "oldBci", group.tagNodeImmediate)));
                        b.string(".tags");
                        b.end();
                    } else {
                        b.statement("searchTags = -1");
                    }
                }
                b.statement("oldBci += " + group.instructionLength);
                b.statement("break");
                b.end();
            }
            b.caseDefault().startCaseBlock();
            b.tree(GeneratorUtils.createShouldNotReachHere("Unexpected bytecode."));
            b.end(); // default block
            b.end(); // switch block
            b.end(); // while block

            b.startAssert().string("searchOp != -1").end();

            b.startAssign("oldBci").string("oldBciBase").end();
            b.declaration(type(int.class), "opCounter", "0");

            b.startWhile().string("oldBci < oldBciTarget").end().startBlock();
            b.declaration(type(short.class), "op", readInstruction("oldBc", "oldBci"));
            b.startSwitch().string("op").end().startBlock();
            for (var groupEntry : groupInstructionsSortedBy(InstructionGroup::new)) {
                InstructionGroup group = groupEntry.getKey();
                if (!group.instrumentation) {
                    // seeing an instrumentation here is a failure
                    continue;
                }
                List<InstructionModel> instructions = groupEntry.getValue();
                for (InstructionModel instruction : instructions) {
                    b.startCase().tree(createInstructionConstant(instruction)).end();
                }
                b.startBlock();

                if (group.tagInstrumentation) {
                    b.startDeclaration(type(int.class), "opTags");
                    b.tree(readTagNode(tagNode.asType(), "oldTagNodes", readImmediate("oldBc", "oldBci", group.tagNodeImmediate)));
                    b.string(".tags");
                    b.end(); // declaration
                    b.startIf().string("searchOp == op && searchTags == opTags").end().startBlock();
                    b.statement("opCounter++");
                    b.end();
                } else {
                    b.startIf().string("searchOp == op").end().startBlock();
                    b.statement("opCounter++");
                    b.end();
                }

                b.statement("oldBci += " + group.instructionLength);
                b.statement("break");
                b.end();
            }
            b.caseDefault().startCaseBlock();
            b.tree(GeneratorUtils.createShouldNotReachHere("Unexpected bytecode."));
            b.end(); // default block
            b.end(); // switch block
            b.end(); // while block

            b.startAssert().string("opCounter > 0").end();

            b.startWhile().string("opCounter > 0").end().startBlock();
            b.declaration(type(short.class), "op", readInstruction("newBc", "newBci"));
            b.startSwitch().string("op").end().startBlock();
            for (var groupEntry : groupInstructionsSortedBy(InstructionGroup::new)) {
                InstructionGroup group = groupEntry.getKey();
                if (!group.instrumentation) {
                    // seeing an instrumentation here is a failure
                    continue;
                }
                List<InstructionModel> instructions = groupEntry.getValue();
                for (InstructionModel instruction : instructions) {
                    b.startCase().tree(createInstructionConstant(instruction)).end();
                }
                b.startBlock();

                if (group.tagInstrumentation) {
                    b.startDeclaration(type(int.class), "opTags");
                    b.tree(readTagNode(tagNode.asType(), "newTagNodes", readImmediate("newBc", "newBci", group.tagNodeImmediate)));
                    b.string(".tags");
                    b.end(); // declaration
                    b.startIf().string("searchOp == op && searchTags == opTags").end().startBlock();
                    b.statement("opCounter--");
                    b.end();
                } else {
                    b.startIf().string("searchOp == op").end().startBlock();
                    b.statement("opCounter--");
                    b.end();
                }

                b.statement("newBci += " + group.instructionLength);
                b.statement("break");
                b.end();
            }
            b.caseDefault().startCaseBlock();
            b.tree(GeneratorUtils.createShouldNotReachHere("Unexpected bytecode."));
            b.end(); // default block
            b.end(); // switch block
            b.end(); // while block

            b.startReturn().string("newBci").end();

            return invalidate;
        }

        private CodeExecutableElement createComputeNewBci() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(FINAL, STATIC), type(int.class), "computeNewBci");
            ex.addParameter(new CodeVariableElement(type(int.class), "oldBci"));
            ex.addParameter(new CodeVariableElement(arrayOf(type(byte.class)), "oldBc"));
            ex.addParameter(new CodeVariableElement(arrayOf(type(byte.class)), "newBc"));
            if (model.enableTagInstrumentation) {
                ex.addParameter(new CodeVariableElement(arrayOf(tagNode.asType()), "oldTagNodes"));
                ex.addParameter(new CodeVariableElement(arrayOf(tagNode.asType()), "newTagNodes"));
            }
            CodeTreeBuilder b = ex.createBuilder();

            b.declaration(type(int.class), "stableBci", "toStableBytecodeIndex(oldBc, oldBci)");
            b.declaration(type(int.class), "newBci", "fromStableBytecodeIndex(newBc, stableBci)");
            b.declaration(type(int.class), "oldBciBase", "fromStableBytecodeIndex(oldBc, stableBci)");

            b.startIf().string("oldBci != oldBciBase").end().startBlock();
            b.lineComment("Transition within an in instrumentation bytecode.");
            b.lineComment("Needs to compute exact location where to continue.");
            b.startAssign("newBci");
            b.startCall("transitionInstrumentationIndex").string("oldBc").string("oldBciBase").string("oldBci").string("newBc").string("newBci");
            if (model.enableTagInstrumentation) {
                b.string("oldTagNodes").string("newTagNodes");
            }
            b.end(); // call
            b.end(); // assign
            b.end(); // if block

            b.startReturn().string("newBci").end();

            return ex;
        }

        /**
         * This function emits the code to map an internal bci to/from a stable value (e.g., a
         * stable bci or instruction index).
         * <p>
         * Assumes the bytecode is stored in a variable {@code bc}.
         *
         * @param b the builder
         * @param targetVariable the name of the variable storing the "target" value to map from.
         * @param stableVariable the name of the variable storing the "stable" value.
         * @param stableIncrement produces a numeric value to increment the stable variable by.
         * @param toStableValue whether to return the stable value or the internal bci.
         */
        private void emitStableBytecodeSearch(CodeTreeBuilder b, String targetVariable, String stableVariable, boolean toStableValue) {
            record InstructionGroup(int instructionLength, boolean instrumentation) implements Comparable<InstructionGroup> {
                InstructionGroup(InstructionModel instr) {
                    this(instr.getInstructionLength(), instr.isInstrumentation());
                }

                // needs a deterministic ordering after grouping
                @Override
                public int compareTo(InstructionGroup o) {
                    int compare = Boolean.compare(this.instrumentation, o.instrumentation);
                    if (compare != 0) {
                        return compare;
                    }
                    compare = Integer.compare(this.instructionLength, o.instructionLength);
                    if (compare != 0) {
                        return compare;
                    }
                    return 0;
                }
            }

            b.declaration(type(int.class), "bci", "0");
            b.declaration(type(int.class), stableVariable, "0");

            String resultVariable;
            String searchVariable;
            if (toStableValue) {
                resultVariable = stableVariable;
                searchVariable = "bci";
            } else {
                resultVariable = "bci";
                searchVariable = stableVariable;
            }

            b.startWhile().string(searchVariable, " != ", targetVariable, " && bci < bc.length").end().startBlock();
            b.startSwitch().tree(readInstruction("bc", "bci")).end().startBlock();

            for (var groupEntry : groupInstructionsSortedBy(InstructionGroup::new)) {
                InstructionGroup group = groupEntry.getKey();
                List<InstructionModel> instructions = groupEntry.getValue();

                for (InstructionModel instruction : instructions) {
                    b.startCase().tree(createInstructionConstant(instruction)).end();
                }
                b.startCaseBlock();
                if (group.instrumentation) {
                    b.statement("bci += " + group.instructionLength);
                } else {
                    b.statement("bci += " + group.instructionLength);
                    b.statement(stableVariable + " += " + group.instructionLength);
                }
                b.statement("break");
                b.end();
            }

            b.caseDefault().startCaseBlock();
            b.tree(GeneratorUtils.createShouldNotReachHere("Invalid bytecode."));
            b.end();

            b.end(); // switch

            b.end(); // while

            b.startIf().string("bci >= bc.length").end().startBlock();
            b.tree(GeneratorUtils.createShouldNotReachHere("Could not translate bytecode index."));
            b.end();

            b.startReturn().string(resultVariable).end();
        }

        private <T extends Comparable<T>> List<Entry<T, List<InstructionModel>>> groupInstructionsSortedBy(Function<InstructionModel, T> constructor) {
            return model.getInstructions().stream()//
                            .collect(deterministicGroupingBy(constructor)).entrySet() //
                            .stream().sorted(Comparator.comparing(e -> e.getKey())).toList();
        }

        private CodeExecutableElement createToStableBytecodeIndex() {
            CodeExecutableElement translate = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(int.class), "toStableBytecodeIndex");
            translate.addParameter(new CodeVariableElement(arrayOf(type(byte.class)), "bc"));
            translate.addParameter(new CodeVariableElement(type(int.class), "searchBci"));
            emitStableBytecodeSearch(translate.createBuilder(), "searchBci", "stableBci", true);
            return translate;
        }

        private CodeExecutableElement createFromStableBytecodeIndex() {
            CodeExecutableElement translate = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(int.class), "fromStableBytecodeIndex");
            translate.addParameter(new CodeVariableElement(arrayOf(type(byte.class)), "bc"));
            translate.addParameter(new CodeVariableElement(type(int.class), "stableSearchBci"));
            emitStableBytecodeSearch(translate.createBuilder(), "stableSearchBci", "stableBci", false);
            return translate;
        }

        private CodeExecutableElement createInvalidate() {
            CodeExecutableElement invalidate = new CodeExecutableElement(Set.of(FINAL), type(void.class), "invalidate");
            invalidate.addParameter(new CodeVariableElement(this.asType(), "newNode"));
            invalidate.addParameter(new CodeVariableElement(type(CharSequence.class), "reason"));
            CodeTreeBuilder b = invalidate.createBuilder();

            b.declaration(arrayOf(type(byte.class)), "bc", "this.bytecodes");
            b.declaration(type(int.class), "bci", "0");
            if (model.enableYield) {
                b.declaration(type(int.class), "continuationIndex", "0");
            }

            b.startAssign("this.oldBytecodes").startStaticCall(type(Arrays.class), "copyOf").string("bc").string("bc.length").end().end();

            b.startStatement().startStaticCall(type(VarHandle.class), "loadLoadFence").end().end();

            b.startWhile().string("bci < bc.length").end().startBlock();
            b.declaration(type(short.class), "op", readInstruction("bc", "bci"));
            b.startSwitch().string("op").end().startBlock();

            for (List<InstructionModel> instructions : groupInstructionsByLength(model.getInstructions())) {
                for (InstructionModel instruction : instructions) {
                    b.startCase().tree(createInstructionConstant(instruction)).end();
                }
                b.startCaseBlock();
                int length = instructions.getFirst().getInstructionLength();
                InstructionModel invalidateInstruction = model.getInvalidateInstruction(length);
                emitInvalidateInstruction(b, "this", "bc", "bci", CodeTreeBuilder.singleString("op"), createInstructionConstant(invalidateInstruction));
                b.statement("bci += " + length);
                b.statement("break");
                b.end();
            }

            b.end();
            b.end(); // switch
            b.end(); // while

            b.statement("reportReplace(this, newNode, reason)");

            return invalidate;
        }

        private CodeExecutableElement createUpdateContinuationRootNodes() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(FINAL), type(void.class), "updateContinuationRootNodes");
            ex.addParameter(new CodeVariableElement(this.asType(), "newNode"));
            ex.addParameter(new CodeVariableElement(type(CharSequence.class), "reason"));
            ex.addParameter(new CodeVariableElement(generic(ArrayList.class, continuationLocation.asType()), "continuationLocations"));
            ex.addParameter(new CodeVariableElement(type(boolean.class), "bytecodeReparsed"));
            CodeTreeBuilder b = ex.createBuilder();

            b.startFor().type(continuationLocation.asType()).string(" continuationLocation : continuationLocations").end().startBlock();

            b.startDeclaration(continuationRootNodeImpl.asType(), "continuationRootNode");
            b.cast(continuationRootNodeImpl.asType());
            b.string("constants[continuationLocation.constantPoolIndex]");
            b.end();

            b.declaration(types.BytecodeLocation, "newLocation");
            b.startIf().string("continuationLocation.bci == -1").end().startBlock();
            b.statement("newLocation = null");
            b.end().startElseBlock();
            b.startAssign("newLocation").string("newNode.getBytecodeLocation(continuationLocation.bci)").end();
            b.end(); // block

            b.startIf().string("bytecodeReparsed").end().startBlock();
            b.startStatement().startCall("continuationRootNode", "updateBytecodeLocation");
            b.string("newLocation");
            b.string("this");
            b.string("newNode");
            b.string("reason");
            b.end(2);
            b.end().startElseBlock();
            b.startStatement().startCall("continuationRootNode", "updateBytecodeLocationWithoutInvalidate");
            b.string("newLocation");
            b.end(2);
            b.end();

            b.end(); // for

            return ex;
        }

        private CodeExecutableElement createAdoptNodesAfterUpdate() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), type(void.class), "adoptNodesAfterUpdate");
            CodeTreeBuilder b = ex.createBuilder();
            b.lineComment("no nodes to adopt");
            return ex;
        }

        private CodeExecutableElement createGetSourceLocation() {
            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "getSourceLocation", new String[]{"bci"}, new TypeMirror[]{type(int.class)});
            ex.getModifiers().add(FINAL);
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("assert validateBytecodeIndex(bci)");

            b.declaration(arrayOf(type(int.class)), "info", "this.sourceInfo");
            b.startIf().string("info == null").end().startBlock();
            b.startReturn().string("null").end();
            b.end();

            b.startFor().string("int i = 0; i < info.length; i += SOURCE_INFO_LENGTH").end().startBlock();
            b.declaration(type(int.class), "startBci", "info[i + SOURCE_INFO_OFFSET_START_BCI]");
            b.declaration(type(int.class), "endBci", "info[i + SOURCE_INFO_OFFSET_END_BCI]");

            b.startIf().string("startBci <= bci && bci < endBci").end().startBlock();
            b.startReturn().string("createSourceSection(sources, info, i)").end();
            b.end();

            b.end();

            b.startReturn().string("null").end();
            return ex;
        }

        private CodeExecutableElement createGetSourceLocations() {
            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "getSourceLocations", new String[]{"bci"}, new TypeMirror[]{type(int.class)});
            ex.getModifiers().add(FINAL);
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("assert validateBytecodeIndex(bci)");

            b.declaration(arrayOf(type(int.class)), "info", "this.sourceInfo");
            b.startIf().string("info == null").end().startBlock();
            b.startReturn().string("null").end();
            b.end();

            b.declaration(type(int.class), "sectionIndex", "0");
            b.startDeclaration(arrayOf(types.SourceSection), "sections").startNewArray(arrayOf(types.SourceSection), CodeTreeBuilder.singleString("8")).end().end();

            b.startFor().string("int i = 0; i < info.length; i += SOURCE_INFO_LENGTH").end().startBlock();
            b.declaration(type(int.class), "startBci", "info[i + SOURCE_INFO_OFFSET_START_BCI]");
            b.declaration(type(int.class), "endBci", "info[i + SOURCE_INFO_OFFSET_END_BCI]");

            b.startIf().string("startBci <= bci && bci < endBci").end().startBlock();

            b.startIf().string("sectionIndex == sections.length").end().startBlock();
            b.startAssign("sections").startStaticCall(type(Arrays.class), "copyOf");
            b.string("sections");
            // Double the size of the array, but cap it at the number of source section entries.
            b.startStaticCall(type(Math.class), "min").string("sections.length * 2").string("info.length / SOURCE_INFO_LENGTH").end();
            b.end(2); // assign
            b.end(); // if

            b.startStatement().string("sections[sectionIndex++] = createSourceSection(sources, info, i)").end();

            b.end(); // if

            b.end(); // for block

            b.startReturn().startStaticCall(type(Arrays.class), "copyOf").string("sections").string("sectionIndex").end().end();
            return ex;
        }

        private CodeExecutableElement createCreateSourceSection() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), types.SourceSection, "createSourceSection");
            ex.addParameter(new CodeVariableElement(generic(List.class, types.Source), "sources"));
            ex.addParameter(new CodeVariableElement(type(int[].class), "info"));
            ex.addParameter(new CodeVariableElement(type(int.class), "index"));

            CodeTreeBuilder b = ex.createBuilder();
            b.declaration(type(int.class), "sourceIndex", "info[index + SOURCE_INFO_OFFSET_SOURCE]");
            b.declaration(type(int.class), "start", "info[index + SOURCE_INFO_OFFSET_START]");
            b.declaration(type(int.class), "length", "info[index + SOURCE_INFO_OFFSET_LENGTH]");

            b.startIf().string("start == -1 && length == -1").end().startBlock();
            b.startReturn().string("sources.get(sourceIndex).createUnavailableSection()").end();
            b.end();

            b.startAssert().string("start >= 0 : ").doubleQuote("invalid source start index").end();
            b.startAssert().string("length >= 0 : ").doubleQuote("invalid source length").end();
            b.startReturn().string("sources.get(sourceIndex).createSection(start, length)").end();
            return ex;
        }

        private CodeExecutableElement createGetSourceSection() {
            CodeExecutableElement ex = GeneratorUtils.override(types.Node, "getSourceSection");
            ex.getAnnotationMirrors().add(new CodeAnnotationMirror(types.CompilerDirectives_TruffleBoundary));
            CodeTreeBuilder b = ex.createBuilder();

            b.declaration(arrayOf(type(int.class)), "info", "this.sourceInfo");
            b.startIf().string("info == null").end().startBlock();
            b.startReturn().string("null").end();
            b.end();

            b.lineComment("The source table encodes a preorder traversal of a logical tree of source sections (with entries in reverse).");
            b.lineComment("The most specific source section corresponds to the \"lowest\" node in the tree that covers the whole bytecode range.");
            b.lineComment("We find this node by iterating the entries from the root until we hit a node that does not cover the bytecode range.");
            b.declaration(type(int.class), "mostSpecific", "-1");

            b.startFor().string("int i = info.length - SOURCE_INFO_LENGTH; i >= 0; i -= SOURCE_INFO_LENGTH").end().startBlock();
            b.startIf();
            b.string("info[i + SOURCE_INFO_OFFSET_START_BCI] != 0 ||").startIndention().newLine();
            b.string("info[i + SOURCE_INFO_OFFSET_END_BCI] != bytecodes.length").end();
            b.end().startBlock();
            b.statement("break");
            b.end(); // if
            b.statement("mostSpecific = i"); // best so far
            b.end(); // for

            b.startIf().string("mostSpecific != -1").end().startBlock();
            b.startReturn();
            b.string("createSourceSection(sources, info, mostSpecific)");
            b.end();
            b.end();
            b.startReturn().string("null").end();
            return ex;
        }

        private CodeExecutableElement createFindInstruction() {
            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "findInstruction", new String[]{"bci"}, new TypeMirror[]{type(int.class)});
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn();
            b.startNew(instructionImpl.asType());
            b.string("this").string("bci").string("readValidBytecode(this.bytecodes, bci)");
            b.end();
            b.end();
            return ex;
        }

        private CodeExecutableElement createValidateBytecodeIndex() {
            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "validateBytecodeIndex", new String[]{"bci"}, new TypeMirror[]{type(int.class)});
            CodeTreeBuilder b = ex.createBuilder();

            b.declaration(type(byte[].class), "bc", "this.bytecodes");
            b.startIf().string("bci < 0 || bci >= bc.length").end().startBlock();
            b.startThrow().startNew(type(IllegalArgumentException.class)).startGroup().doubleQuote("Bytecode index out of range ").string(" + bci").end().end().end();
            b.end();

            b.declaration(type(int.class), "op", "readValidBytecode(bc, bci)");

            int maxId = model.getInstructions().stream().max(Comparator.comparingInt(i -> i.getId())).get().getId();
            b.startIf().string("op < 0 || op > ").string(maxId).end().startBlock();
            b.startThrow().startNew(type(IllegalArgumentException.class)).startGroup().doubleQuote("Invalid op at bytecode index ").string(" + op").end().end().end();
            b.end();

            // we could do more validations here, but they would likely be too expensive

            b.returnTrue();
            return ex;
        }

        private CodeExecutableElement createHasSourceInformation() {
            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "hasSourceInformation");
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return sourceInfo != null");
            return ex;
        }

        private CodeExecutableElement createGetSourceInformation() {
            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "getSourceInformation");
            CodeTreeBuilder b = ex.createBuilder();
            b.startIf().string("sourceInfo == null").end().startBlock();
            b.returnNull();
            b.end();
            b.startReturn();
            b.startNew("SourceInformationList").string("this").end();
            b.end();
            return ex;
        }

        private CodeExecutableElement createGetSourceInformationTree() {
            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "getSourceInformationTree");
            CodeTreeBuilder b = ex.createBuilder();
            b.startIf().string("sourceInfo == null").end().startBlock();
            b.returnNull();
            b.end();
            b.startReturn();
            b.string("SourceInformationTreeImpl.parse(this)");
            b.end();
            return ex;
        }

        private CodeExecutableElement createGetExceptionHandlers() {
            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "getExceptionHandlers");
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn();
            b.startNew("ExceptionHandlerList").string("this").end();
            b.end();
            return ex;
        }

        private CodeExecutableElement createGetTagTree() {
            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "getTagTree");
            CodeTreeBuilder b = ex.createBuilder();
            if (model.enableTagInstrumentation) {
                b.startIf().string("this.tagRoot == null").end().startBlock();
                b.statement("return null");
                b.end();
                b.statement("return this.tagRoot.root");
            } else {
                b.statement("return null");
            }
            return ex;
        }

    }

    final class BytecodeNodeElement extends CodeTypeElement {

        private static final String METADATA_FIELD_NAME = "osrMetadata_";
        private static final String FORCE_UNCACHED_THRESHOLD = "Integer.MIN_VALUE";
        private final InterpreterTier tier;
        private final Map<InstructionModel, CodeExecutableElement> doInstructionMethods = new LinkedHashMap<>();

        BytecodeNodeElement(InterpreterTier tier) {
            super(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, tier.bytecodeClassName());
            this.tier = tier;
            this.setSuperClass(abstractBytecodeNode.asType());
            this.addAll(createContinueAt());
            this.getAnnotationMirrors().add(new CodeAnnotationMirror(types.DenyReplace));

            if (tier.isUncached()) {
                this.add(createUncachedConstructor());
                this.add(new CodeVariableElement(Set.of(PRIVATE), type(int.class), "uncachedExecuteCount_"));
            } else if (tier.isCached()) {
                this.add(createCachedConstructor());
                this.add(compFinal(1, new CodeVariableElement(Set.of(PRIVATE), arrayOf(types.Node), "cachedNodes_")));
                this.add(compFinal(1, new CodeVariableElement(Set.of(PRIVATE, FINAL), arrayOf(type(boolean.class)), "exceptionProfiles_")));
                if (model.epilogExceptional != null) {
                    this.add(child(new CodeVariableElement(Set.of(PRIVATE), getCachedDataClassType(model.epilogExceptional.operation.instruction), "epilogExceptionalNode_")));
                }

                if (model.usesBoxingElimination()) {
                    this.add(compFinal(1, new CodeVariableElement(Set.of(PRIVATE, FINAL), arrayOf(type(byte.class)), "localTags_")));
                    if (model.enableYield) {
                        this.add(compFinal(new CodeVariableElement(Set.of(PRIVATE, VOLATILE), types.Assumption, "stableTagsAssumption_")));
                    }
                }

                this.add(createLoadConstantCompiled());
                this.add(createAdoptNodesAfterUpdate());
                this.addAll(createBranchProfileMembers());

                // Define the members required to support OSR.
                this.getImplements().add(types.BytecodeOSRNode);
                this.add(createExecuteOSR());
                this.add(createPrepareOSR());
                this.add(createCopyIntoOSRFrame());
                this.addAll(createMetadataMembers());
                this.addAll(createStoreAndRestoreParentFrameMethods());
            } else if (tier.isUninitialized()) {
                this.add(GeneratorUtils.createConstructorUsingFields(Set.of(), this));
            } else {
                throw new AssertionError("invalid tier");
            }

            this.add(createSetUncachedThreshold());
            this.add(createGetTier());

            if (!tier.isUninitialized()) {
                // uninitialized does not need a copy constructor as the default constructor is
                // already copying.
                this.add(createCopyConstructor());
                this.add(createResolveThrowable());
                this.add(createResolveHandler());

                if (model.epilogExceptional != null) {
                    this.add(createDoEpilogExceptional());
                }
                if (model.enableTagInstrumentation) {
                    this.add(createDoTagExceptional());
                }
                if (model.interceptControlFlowException != null) {
                    this.add(createResolveControlFlowException());
                }
            }

            if (model.usesBoxingElimination()) {
                this.add(createGetLocalTags());
                this.add(createGetLocalValue());
                this.add(createSetLocalValue());

                this.add(createGetLocalValueInternal(type(Object.class)));
                this.add(createSetLocalValueInternal(type(Object.class)));

                for (TypeMirror boxingType : model.boxingEliminatedTypes) {
                    this.add(createGetLocalValueInternal(boxingType));
                    this.add(createSetLocalValueInternal(boxingType));
                }

                if (tier.isCached()) {
                    this.add(createSetLocalValueImpl());
                    this.add(createSpecializeSlotTag());
                    this.add(createGetCachedLocalTag());
                    this.add(createSetCachedLocalTag());
                }
                this.add(createGetCachedLocalTagInternal());
                this.add(createSetCachedLocalTagInternal());
                if (model.enableYield) {
                    this.add(createCheckStableTagsAssumption());
                }
            } else {
                // generated in AbstractBytecodeNode
            }

            this.add(createToCached());
            this.add(createUpdate());
            this.add(createCloneUninitialized());
            if (cloneUninitializedNeedsUnquickenedBytecode()) {
                this.add(createUnquickenBytecode());
            }
            this.add(createGetCachedNodes());
            this.add(createGetBranchProfiles());
            this.add(createFindBytecodeIndex1());
            this.add(createFindBytecodeIndex2());
            if (model.storeBciInFrame) {
                this.add(createGetBytecodeIndex());
            } else {
                this.add(createFindBytecodeIndexOfOperationNode());
            }
            this.add(createToString());
        }

        private CodeExecutableElement createExecuteOSR() {
            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeOSRNode, "executeOSR",
                            new String[]{"frame", "target", "unused"},
                            new TypeMirror[]{types.VirtualFrame, type(long.class), type(Object.class)});
            CodeTreeBuilder b = ex.getBuilder();

            if (model.enableYield) {
                b.declaration(types.VirtualFrame, "localFrame");
                b.startIf().string(decodeUseContinuationFrame("target")).string(" /* use continuation frame */").end().startBlock();
                b.startAssign("localFrame");
                b.cast(types.MaterializedFrame);
                startGetFrame(b, "frame", type(Object.class), false).string(COROUTINE_FRAME_INDEX).end();
                b.end();
                b.end().startElseBlock();
                b.statement("localFrame = frame");
                b.end();
            }

            b.startReturn().startCall("continueAt");
            b.string("getRoot()");
            b.string("frame");
            if (model.enableYield) {
                b.string("localFrame");
                b.string(clearUseContinuationFrame("target"));
            } else {
                b.string("target");
            }
            b.end(2);

            return ex;
        }

        private CodeExecutableElement createPrepareOSR() {
            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeOSRNode, "prepareOSR",
                            new String[]{"target"},
                            new TypeMirror[]{type(long.class)});
            CodeTreeBuilder b = ex.getBuilder();
            b.lineComment("do nothing");
            return ex;
        }

        private CodeExecutableElement createCopyIntoOSRFrame() {
            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeOSRNode, "copyIntoOSRFrame",
                            new String[]{"osrFrame", "parentFrame", "target", "targetMetadata"},
                            new TypeMirror[]{types.VirtualFrame, types.VirtualFrame, type(long.class), type(Object.class)});
            CodeTreeBuilder b = ex.getBuilder();
            // default behaviour. we just need to explicitly implement the long overload.
            b.startStatement().startCall("transferOSRFrame");
            b.string("osrFrame");
            b.string("parentFrame");
            b.string("target");
            b.string("targetMetadata");
            b.end(2);
            return ex;
        }

        private List<CodeElement<Element>> createMetadataMembers() {
            CodeVariableElement osrMetadataField = compFinal(new CodeVariableElement(Set.of(PRIVATE), context.getDeclaredType(Object.class), METADATA_FIELD_NAME));

            CodeExecutableElement getOSRMetadata = GeneratorUtils.override(types.BytecodeOSRNode, "getOSRMetadata");
            getOSRMetadata.getBuilder().startReturn().string(METADATA_FIELD_NAME).end();

            CodeExecutableElement setOSRMetadata = GeneratorUtils.override(types.BytecodeOSRNode, "setOSRMetadata", new String[]{"osrMetadata"});
            setOSRMetadata.getBuilder().startAssign(METADATA_FIELD_NAME).string("osrMetadata").end();

            return List.of(osrMetadataField, getOSRMetadata, setOSRMetadata);
        }

        private List<CodeExecutableElement> createStoreAndRestoreParentFrameMethods() {
            // Append parent frame to end of array so that regular argument reads work as expected.
            CodeExecutableElement storeParentFrameInArguments = GeneratorUtils.override(types.BytecodeOSRNode, "storeParentFrameInArguments", new String[]{"parentFrame"});
            CodeTreeBuilder sb = storeParentFrameInArguments.getBuilder();
            sb.declaration(type(Object[].class), "parentArgs", "parentFrame.getArguments()");
            sb.declaration(type(Object[].class), "result", "Arrays.copyOf(parentArgs, parentArgs.length + 1)");
            sb.statement("result[result.length - 1] = parentFrame");
            sb.startReturn().string("result").end();

            CodeExecutableElement restoreParentFrameFromArguments = GeneratorUtils.override(types.BytecodeOSRNode, "restoreParentFrameFromArguments", new String[]{"arguments"});
            CodeTreeBuilder rb = restoreParentFrameFromArguments.getBuilder();
            rb.startReturn().cast(types.Frame).string("arguments[arguments.length - 1]").end();

            return List.of(storeParentFrameInArguments, restoreParentFrameFromArguments);
        }

        final class InterpreterStateElement extends CodeTypeElement {
            InterpreterStateElement() {
                super(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "InterpreterState");
                if (!model.enableYield) {
                    // Without continuations, this state class is unnecessary. Just pass the sp.
                    throw new AssertionError("A InterpreterState class should only be generated when continuations are enabled.");
                }
                this.add(new CodeVariableElement(Set.of(FINAL), type(boolean.class), "isContinuation"));
                this.add(new CodeVariableElement(Set.of(FINAL), type(int.class), "sp"));
                this.add(createConstructorUsingFields(Set.of(), this));
            }
        }

        private boolean useOperationNodeForBytecodeIndex() {
            return !model.storeBciInFrame && tier == InterpreterTier.CACHED;
        }

        private boolean useFrameForBytecodeIndex() {
            return model.storeBciInFrame || tier == InterpreterTier.UNCACHED;
        }

        private CodeExecutableElement createGetLocalValue() {
            if (!model.usesBoxingElimination()) {
                throw new AssertionError("Not supported.");
            }

            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "getLocalValue",
                            new String[]{"bci", "frame", "localOffset"},
                            new TypeMirror[]{type(int.class), types.Frame, type(int.class)});
            ex.getModifiers().add(FINAL);

            CodeTreeBuilder b = ex.createBuilder();
            b.statement("assert validateBytecodeIndex(bci)");
            AbstractBytecodeNodeElement.buildVerifyLocalsIndex(b);
            AbstractBytecodeNodeElement.buildVerifyFrameDescriptor(b, true);

            b.declaration(type(int.class), "frameIndex", "USER_LOCALS_START_INDEX + localOffset");
            if (tier.isCached()) {
                b.startTryBlock();
                b.declaration(type(byte.class), "tag");
                b.startIf().startStaticCall(types.CompilerDirectives, "inInterpreter").end().end().startBlock();
                b.lineComment("Resolving the local index is expensive. Don't do it in the interpreter.");
                b.startAssign("tag");
                b.string("frame.getTag(frameIndex)");
                b.end();

                b.end().startElseBlock();

                if (model.enableBlockScoping) {
                    b.declaration(type(int.class), "localIndex", "localOffsetToLocalIndex(bci, localOffset)");
                    b.startAssign("tag").string("getCachedLocalTagInternal(this.localTags_, localIndex)").end();
                } else {
                    b.startAssign("tag").string("getCachedLocalTag(localOffset)").end();
                }
                b.end();

                b.startSwitch().string("tag").end().startBlock();
                for (TypeMirror boxingType : model.boxingEliminatedTypes) {
                    b.startCase().staticReference(frameTagsElement.get(boxingType)).end();
                    b.startCaseBlock();

                    b.startReturn();
                    startExpectFrame(b, "frame", boxingType, false).string("frameIndex").end();
                    b.end();
                    b.end(); // case block
                }

                b.startCase().staticReference(frameTagsElement.getObject()).end();
                b.startCaseBlock();
                b.startReturn();
                startExpectFrame(b, "frame", type(Object.class), false).string("frameIndex").end();
                b.end();
                b.end(); // case block

                b.startCase().staticReference(frameTagsElement.getIllegal()).end();
                b.startCaseBlock();
                b.startReturn();
                if (model.defaultLocalValueExpression != null) {
                    b.string("DEFAULT_LOCAL_VALUE");
                } else {
                    b.string("null");
                }
                b.end();
                b.end(); // case block

                b.caseDefault().startCaseBlock();
                b.tree(GeneratorUtils.createShouldNotReachHere("Unexpected tag"));
                b.end();

                b.end(); // switch block
                b.end().startCatchBlock(types.UnexpectedResultException, "ex");
                b.startReturn().string("ex.getResult()").end();
                b.end(); // catch
            } else {
                b.startIf().string("frame.isObject(frameIndex)").end().startBlock();
                b.startReturn().string("frame.getObject(frameIndex)").end();
                b.end();
                b.statement("return null");
            }

            return ex;
        }

        private CodeExecutableElement createSetLocalValue() {
            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "setLocalValue",
                            new String[]{"bci", "frame", "localOffset", "value"},
                            new TypeMirror[]{type(int.class), types.Frame, type(int.class), type(Object.class)});
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("assert validateBytecodeIndex(bci)");
            AbstractBytecodeNodeElement.buildVerifyLocalsIndex(b);
            AbstractBytecodeNodeElement.buildVerifyFrameDescriptor(b, true);
            if (model.usesBoxingElimination() && tier.isCached()) {
                b.startStatement().startCall("setLocalValueImpl");
                b.string("frame").string("localOffset").string("value");
                if (model.enableBlockScoping) {
                    b.string("bci");
                }
                b.end().end(); // call, statement
            } else {
                b.startStatement();
                b.startCall("frame", getSetMethod(type(Object.class))).string("localOffset + " + USER_LOCALS_START_INDEX).string("value").end();
                b.end();
            }
            return ex;
        }

        private CodeExecutableElement createSetLocalValueImpl() {
            if (!model.usesBoxingElimination() || !tier.isCached()) {
                throw new AssertionError("Not supported.");
            }

            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "setLocalValueImpl");
            ex.addParameter(new CodeVariableElement(types.Frame, "frame"));
            ex.addParameter(new CodeVariableElement(type(int.class), "localOffset"));
            ex.addParameter(new CodeVariableElement(type(Object.class), "value"));

            CodeTreeBuilder b = ex.createBuilder();

            b.declaration(type(int.class), "frameIndex", "localOffset + " + USER_LOCALS_START_INDEX);

            if (model.enableBlockScoping) {
                ex.addParameter(new CodeVariableElement(type(int.class), "bci"));
                b.declaration(type(int.class), "localIndex", "localOffsetToLocalIndex(bci, localOffset)");
                b.declaration(type(byte.class), "oldTag", "getCachedLocalTagInternal(this.localTags_, localIndex)");
            } else {
                b.declaration(type(byte.class), "oldTag", "getCachedLocalTag(localOffset)");
            }
            b.declaration(type(byte.class), "newTag");

            b.startSwitch().string("oldTag").end().startBlock();

            for (TypeMirror boxingType : model.boxingEliminatedTypes) {
                b.startCase().staticReference(frameTagsElement.get(boxingType)).end();
                b.startCaseBlock();
                String primitiveValue = boxingType.toString().toLowerCase() + "Value";
                b.startIf().instanceOf("value", ElementUtils.boxType(boxingType), primitiveValue).end().startBlock();
                b.startStatement();
                b.startCall("frame", getSetMethod(boxingType)).string("frameIndex").string(primitiveValue).end();
                b.end(); // statement
                b.statement("return");
                b.end(); // if block
                b.startElseBlock();
                b.startAssign("newTag").staticReference(frameTagsElement.getObject()).end();
                b.end();
                b.statement("break");
                b.end(); // case block
            }

            b.startCase().staticReference(frameTagsElement.getObject()).end();
            b.startCaseBlock();
            b.startStatement();
            b.startCall("frame", getSetMethod(type(Object.class))).string("frameIndex").string("value").end();
            b.end();
            b.statement("return");
            b.end(); // case block
            b.caseDefault().startCaseBlock();
            b.startAssign("newTag").string("specializeSlotTag(value)").end();
            b.statement("break");
            b.end();
            b.end(); // switch block

            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());

            if (model.enableBlockScoping) {
                b.statement("setCachedLocalTagInternal(this.localTags_, localIndex, newTag)");
                b.statement("setLocalValueImpl(frame, localOffset, value, bci)");
            } else {
                b.statement("setCachedLocalTagInternal(this.localTags_, localOffset, newTag)");
                b.statement("setLocalValueImpl(frame, localOffset, value)");
            }

            return ex;
        }

        private CodeExecutableElement createSetLocalValueInternal(TypeMirror specializedType) {
            if (!model.usesBoxingElimination()) {
                throw new AssertionError("Not supported.");
            }
            boolean generic = ElementUtils.isObject(specializedType);
            String suffix;
            if (ElementUtils.isObject(specializedType)) {
                suffix = "";
            } else {
                suffix = ElementUtils.firstLetterUpperCase(ElementUtils.getSimpleName(specializedType));
            }

            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "setLocalValueInternal" + suffix,
                            new String[]{"frame", "localOffset", "localIndex", "value"},
                            new TypeMirror[]{types.Frame, type(int.class), type(int.class), specializedType});
            CodeTreeBuilder b = ex.createBuilder();
            AbstractBytecodeNodeElement.buildVerifyFrameDescriptor(b, true);
            if (tier.isCached()) {
                b.declaration(type(int.class), "frameIndex", "USER_LOCALS_START_INDEX + localOffset");

                b.startDeclaration(type(byte.class), "oldTag");
                b.startCall("getCachedLocalTag");
                b.string("localIndex");
                b.end(); // call
                b.end(); // declaration

                b.declaration(type(byte.class), "newTag");

                b.startSwitch().string("oldTag").end().startBlock();

                for (TypeMirror boxingType : model.boxingEliminatedTypes) {
                    if (!generic && !ElementUtils.typeEquals(boxingType, specializedType)) {
                        continue;
                    }

                    b.startCase().staticReference(frameTagsElement.get(boxingType)).end();
                    b.startCaseBlock();

                    if (generic) {
                        String primitiveValue = boxingType.toString().toLowerCase() + "Value";
                        b.startIf().instanceOf("value", ElementUtils.boxType(boxingType), primitiveValue).end().startBlock();
                        b.startStatement();
                        b.startCall("frame", getSetMethod(boxingType)).string("frameIndex").string(primitiveValue).end();
                        b.end(); // statement
                        b.statement("return");
                        b.end(); // if block
                        b.startElseBlock();
                        b.startAssign("newTag").staticReference(frameTagsElement.getObject()).end();
                        b.end();
                        b.statement("break");
                    } else {
                        b.startStatement();
                        b.startCall("frame", getSetMethod(boxingType)).string("frameIndex").string("value").end();
                        b.end(); // statement
                        b.statement("return");
                    }
                    b.end(); // case block
                }

                b.startCase().staticReference(frameTagsElement.getObject()).end();
                b.startCaseBlock();
                b.startStatement();
                b.startCall("frame", getSetMethod(type(Object.class))).string("frameIndex").string("value").end();
                b.end();
                b.statement("return");
                b.end(); // case block
                b.caseDefault().startCaseBlock();
                b.startAssign("newTag").string("specializeSlotTag(value)").end();
                b.statement("break");
                b.end();
                b.end(); // switch block

                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                b.startStatement().startCall("setCachedLocalTagInternal");
                b.string("this.localTags_");
                b.string("localIndex");
                b.string("newTag");
                b.end(2);
                b.statement("setLocalValueInternal(frame, localOffset, localIndex, value)");

            } else {
                b.startStatement();
                b.startCall("frame", getSetMethod(type(Object.class))).string("USER_LOCALS_START_INDEX + localOffset").string("value").end();
                b.end();
            }
            return ex;
        }

        private CodeExecutableElement createGetLocalValueInternal(TypeMirror specializedType) {
            if (!model.usesBoxingElimination()) {
                throw new AssertionError("Not supported.");
            }

            boolean generic = ElementUtils.isObject(specializedType);
            String suffix;
            if (generic) {
                suffix = "";
            } else {
                suffix = ElementUtils.firstLetterUpperCase(ElementUtils.getSimpleName(specializedType));
            }

            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "getLocalValueInternal" + suffix,
                            new String[]{"frame", "localOffset", "localIndex"},
                            new TypeMirror[]{types.Frame, type(int.class), type(int.class)});
            ex.getModifiers().add(FINAL);

            CodeTreeBuilder b = ex.createBuilder();
            AbstractBytecodeNodeElement.buildVerifyFrameDescriptor(b, true);

            if (tier.isCached()) {
                if (generic) {
                    b.declaration(type(int.class), "frameIndex", "USER_LOCALS_START_INDEX + localOffset");
                    b.startTryBlock();
                    b.startDeclaration(type(byte.class), "tag").startCall("getCachedLocalTag");
                    b.string("localIndex");
                    b.end(2);

                    b.startSwitch().string("tag").end().startBlock();
                    for (TypeMirror boxingType : model.boxingEliminatedTypes) {
                        b.startCase().staticReference(frameTagsElement.get(boxingType)).end();
                        b.startCaseBlock();

                        b.startReturn();
                        startExpectFrame(b, "frame", boxingType, false).string("frameIndex").end();
                        b.end();
                        b.end(); // case block
                    }

                    b.startCase().staticReference(frameTagsElement.getObject()).end();
                    b.startCaseBlock();
                    b.startReturn();
                    startExpectFrame(b, "frame", type(Object.class), false).string("frameIndex").end();
                    b.end();
                    b.end(); // case block

                    b.startCase().staticReference(frameTagsElement.getIllegal()).end();
                    b.startCaseBlock();
                    if (model.defaultLocalValueExpression != null) {
                        b.startReturn();
                        b.string("DEFAULT_LOCAL_VALUE");
                        b.end();
                    } else {
                        b.startThrow().startNew(types.FrameSlotTypeException).end().end();
                    }
                    b.end(); // case block

                    b.caseDefault().startCaseBlock();
                    b.tree(GeneratorUtils.createShouldNotReachHere("unexpected tag"));
                    b.end();

                    b.end(); // switch block
                    b.end().startCatchBlock(types.UnexpectedResultException, "ex");
                    b.startReturn().string("ex.getResult()").end();
                    b.end(); // catch
                } else {
                    b.startReturn();
                    startExpectFrame(b, "frame", specializedType, false).string("USER_LOCALS_START_INDEX + localOffset").end();
                    b.end();
                }
            } else {
                if (generic) {
                    b.startReturn().string("frame.getObject(USER_LOCALS_START_INDEX + localOffset)").end();
                } else {
                    b.declaration(type(Object.class), "value", "frame.getObject(USER_LOCALS_START_INDEX + localOffset)");
                    b.startIf().string("value instanceof ").type(ElementUtils.boxType(specializedType)).string(" castValue").end().startBlock();
                    b.startReturn().string("castValue").end();
                    b.end();
                    b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                    b.startThrow().startNew(types.UnexpectedResultException).string("value").end().end();
                }
            }

            return ex;
        }

        private CodeExecutableElement createSetCachedLocalTag() {
            if (!model.usesBoxingElimination() || !tier.isCached()) {
                throw new AssertionError("Not supported.");
            }

            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "setCachedLocalTag");
            ex.addParameter(new CodeVariableElement(type(int.class), "localIndex"));
            ex.addParameter(new CodeVariableElement(type(byte.class), "tag"));
            CodeTreeBuilder b = ex.createBuilder();

            b.declaration(arrayOf(type(byte.class)), "localTags", "this.localTags_");
            b.startIf().string("localIndex < 0 || localIndex >= localTags.length").end().startBlock();
            emitThrowIllegalArgumentException(b, "Invalid local offset");
            b.end();
            b.startStatement().startCall("setCachedLocalTagInternal");
            b.string("localTags");
            b.string("localIndex");
            b.string("tag");
            b.end(2);

            return ex;
        }

        private CodeExecutableElement createGetCachedLocalTag() {
            if (!model.usesBoxingElimination() || !tier.isCached()) {
                throw new AssertionError("Not supported.");
            }

            CodeExecutableElement ex = new CodeExecutableElement(Set.of(FINAL), type(byte.class), "getCachedLocalTag");
            ex.addParameter(new CodeVariableElement(type(int.class), "localIndex"));

            CodeTreeBuilder b = ex.createBuilder();

            b.declaration(arrayOf(type(byte.class)), "localTags", "this.localTags_");
            b.startIf().string("localIndex < 0 || localIndex >= localTags.length").end().startBlock();
            emitThrowIllegalArgumentException(b, "Invalid local offset");
            b.end();
            b.startReturn().startCall("getCachedLocalTagInternal");
            b.string("localTags");
            b.string("localIndex");
            b.end(2);

            return ex;
        }

        private CodeExecutableElement createSetCachedLocalTagInternal() {
            if (!model.usesBoxingElimination()) {
                throw new AssertionError("Not supported.");
            }

            CodeExecutableElement ex = GeneratorUtils.override(abstractBytecodeNode.setCachedLocalTagInternal);
            CodeTreeBuilder b = ex.createBuilder();

            if (tier.isCached()) {
                b.tree(createNeverPartOfCompilation());
                b.statement(writeByte("localTags", "localIndex", "tag"));
                // Invalidate call targets.
                b.startStatement().startCall("reportReplace");
                b.string("this").string("this").doubleQuote("local tags updated");
                b.end(2);
                if (model.usesBoxingElimination() && model.enableYield) {
                    // Invalidate continuation call targets.
                    b.declaration(types.Assumption, "oldStableTagsAssumption", "this.stableTagsAssumption_");
                    b.startIf().string("oldStableTagsAssumption != null").end().startBlock();
                    b.startAssign("this.stableTagsAssumption_").startStaticCall(types.Assumption, "create");
                    b.doubleQuote("Stable local tags");
                    b.end(2);
                    b.startStatement().startCall("oldStableTagsAssumption.invalidate").doubleQuote("local tags updated").end(2);
                    b.end(); // if
                }
            } else {
                // nothing to do
            }
            return ex;
        }

        private CodeExecutableElement createGetCachedLocalTagInternal() {
            if (!model.usesBoxingElimination()) {
                throw new AssertionError("Not supported.");
            }

            CodeExecutableElement ex = GeneratorUtils.override(abstractBytecodeNode.getCachedLocalTagInternal);
            CodeTreeBuilder b = ex.createBuilder();

            if (tier.isCached()) {
                b.startReturn();
                b.string(readByte("localTags", "localIndex"));
                b.end();
            } else {
                b.startReturn().staticReference(frameTagsElement.getObject()).end();
            }
            return ex;
        }

        private CodeExecutableElement createCheckStableTagsAssumption() {
            if (!model.usesBoxingElimination() || !model.enableYield) {
                throw new AssertionError("Not supported.");
            }

            CodeExecutableElement ex = GeneratorUtils.override(abstractBytecodeNode.checkStableTagsAssumption);
            CodeTreeBuilder b = ex.createBuilder();

            if (tier.isCached()) {
                b.startReturn().string("this.stableTagsAssumption_.isValid()").end();
            } else {
                b.startReturn().string("true").end();
            }
            return ex;
        }

        private CodeExecutableElement createSpecializeSlotTag() {
            if (!model.usesBoxingElimination() || !tier.isCached()) {
                throw new AssertionError("Not supported.");
            }

            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(byte.class), "specializeSlotTag");
            ex.addParameter(new CodeVariableElement(type(Object.class), "value"));
            CodeTreeBuilder b = ex.createBuilder();
            boolean elseIf = false;
            for (TypeMirror boxingType : model.boxingEliminatedTypes) {
                elseIf = b.startIf(elseIf);
                b.string("value instanceof ").type(ElementUtils.boxType(boxingType)).end().startBlock();
                b.startReturn().staticReference(frameTagsElement.get(boxingType)).end();
                b.end();
            }
            b.startElseBlock();
            b.startReturn().staticReference(frameTagsElement.getObject()).end();
            b.end();

            return ex;
        }

        private CodeExecutableElement createFindBytecodeIndex1() {
            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "findBytecodeIndex",
                            new String[]{"frameInstance"}, new TypeMirror[]{types.FrameInstance});
            CodeTreeBuilder b = ex.createBuilder();

            if (useOperationNodeForBytecodeIndex()) {
                b.declaration(types.Node, "prev", "null");
                b.startFor().string("Node current = frameInstance.getCallNode(); current != null; current = current.getParent()").end().startBlock();
                b.startIf().string("current == this && prev != null").end().startBlock();
                b.statement("return findBytecodeIndexOfOperationNode(prev)");
                b.end();
                b.statement("prev = current");
                b.end();
                b.startReturn().string("-1").end();
            } else if (useFrameForBytecodeIndex()) {
                CodeTree getFrame = CodeTreeBuilder.createBuilder() //
                                .startCall("frameInstance", "getFrame") //
                                .staticReference(types.FrameInstance_FrameAccess, "READ_ONLY") //
                                .end().build();
                if (model.enableYield) {
                    /**
                     * If the frame is from a continuation, the bci will be in the locals frame,
                     * which is stored in slot COROUTINE_FRAME_INDEX.
                     */
                    b.declaration(types.Frame, "frame", getFrame);

                    if (model.defaultLocalValueExpression == null) {
                        b.startIf().string("frame.isObject(" + COROUTINE_FRAME_INDEX + ")").end().end().startBlock();
                        b.startAssign("frame").cast(types.Frame).string("frame.getObject(" + COROUTINE_FRAME_INDEX + ")").end();
                        b.end();
                    } else {
                        b.declaration(type(Object.class), "coroutineFrame", "frame.getObject(" + COROUTINE_FRAME_INDEX + ")");
                        b.startIf().string("coroutineFrame != DEFAULT_LOCAL_VALUE").end().end().startBlock();
                        b.startAssign("frame").cast(types.Frame).string("coroutineFrame").end();
                        b.end();
                    }

                    b.startReturn();
                    b.startCall("frame", "getInt");
                    b.string(BCI_INDEX);
                    b.end(2);
                } else {
                    b.startReturn();
                    b.startCall(getFrame, "getInt");
                    b.string(BCI_INDEX);
                    b.end(2);
                }
            } else {
                b.startReturn().string("-1").end();
            }

            return withTruffleBoundary(ex);
        }

        private CodeExecutableElement createFindBytecodeIndex2() {
            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "findBytecodeIndex",
                            new String[]{"frame", "node"}, new TypeMirror[]{types.Frame, types.Node});
            CodeTreeBuilder b = ex.createBuilder();

            if (useOperationNodeForBytecodeIndex()) {
                b.startIf().string("node != null").end().startBlock();
                b.statement("return findBytecodeIndexOfOperationNode(node)");
                b.end();
                b.startReturn().string("-1").end();
            } else if (useFrameForBytecodeIndex()) {
                b.startReturn().string("frame.getInt(" + BCI_INDEX + ")").end();
            } else {
                b.startReturn().string("-1").end();
            }

            return ex;
        }

        private CodeExecutableElement createGetBytecodeIndex() {
            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "getBytecodeIndex", new String[]{"frame"}, new TypeMirror[]{types.Frame});
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn().string("frame.getInt(" + BCI_INDEX + ")").end();
            return ex;
        }

        private CodeExecutableElement createGetLocalTags() {
            CodeExecutableElement ex = GeneratorUtils.override((DeclaredType) abstractBytecodeNode.asType(), "getLocalTags");
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn();
            switch (tier) {
                case UNCACHED:
                case UNINITIALIZED:
                    b.string("null");
                    break;
                case CACHED:
                    b.string("this.localTags_");
                    break;
            }
            b.end();
            return ex;
        }

        private CodeExecutableElement createGetCachedNodes() {
            CodeExecutableElement ex = GeneratorUtils.override((DeclaredType) abstractBytecodeNode.asType(), "getCachedNodes");
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn();
            switch (tier) {
                case UNCACHED:
                case UNINITIALIZED:
                    b.string("null");
                    break;
                case CACHED:
                    b.string("this.cachedNodes_");
                    break;
            }
            b.end();
            return ex;
        }

        private CodeExecutableElement createGetBranchProfiles() {
            CodeExecutableElement ex = GeneratorUtils.override((DeclaredType) abstractBytecodeNode.asType(), "getBranchProfiles");
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn();
            switch (tier) {
                case UNCACHED:
                case UNINITIALIZED:
                    b.string("null");
                    break;
                case CACHED:
                    b.string("this.branchProfiles_");
                    break;
            }
            b.end();
            return ex;
        }

        private boolean cloneUninitializedNeedsUnquickenedBytecode() {
            // If the node supports BE/quickening, cloneUninitialized should unquicken the bytecode.
            // Uncached nodes don't rewrite bytecode, so we only need to unquicken if cached.
            return (model.usesBoxingElimination() || model.enableQuickening) && tier.isCached();
        }

        private CodeExecutableElement createCloneUninitialized() {
            CodeExecutableElement ex = GeneratorUtils.override((DeclaredType) abstractBytecodeNode.asType(), "cloneUninitialized");
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn();
            b.startNew(tier.friendlyName + "BytecodeNode");
            for (VariableElement var : ElementFilter.fieldsIn(abstractBytecodeNode.getEnclosedElements())) {
                b.startGroup();
                if (var.getSimpleName().contentEquals("tagRoot")) {
                    b.string("tagRoot != null ? ").cast(tagRootNode.asType()).string("tagRoot.deepCopy() : null");
                } else if (var.getSimpleName().contentEquals("bytecodes")) {
                    if (cloneUninitializedNeedsUnquickenedBytecode()) {
                        b.startCall("unquickenBytecode").string("this.bytecodes").end();
                    } else {
                        b.startStaticCall(type(Arrays.class), "copyOf");
                        b.string("this.bytecodes").string("this.bytecodes.length");
                        b.end();
                    }
                } else {
                    b.string("this.", var.getSimpleName().toString());
                }
                b.end();
            }
            if (tier.isCached() && model.usesBoxingElimination()) {
                b.string("this.localTags_.length");
            }
            b.end();
            b.end();
            return ex;
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

        private CodeExecutableElement createToCached() {
            CodeExecutableElement ex = GeneratorUtils.override(ElementUtils.findInstanceMethod(abstractBytecodeNode, "toCached", null));
            CodeTreeBuilder b = ex.createBuilder();
            switch (tier) {
                case UNCACHED:
                case UNINITIALIZED:
                    b.startReturn();
                    b.startNew(InterpreterTier.CACHED.friendlyName + "BytecodeNode");
                    for (VariableElement var : ElementFilter.fieldsIn(abstractBytecodeNode.getEnclosedElements())) {
                        b.string("this.", var.getSimpleName().toString());
                    }
                    if (model.usesBoxingElimination()) {
                        b.string("numLocals");
                    }
                    b.end();
                    b.end();
                    break;
                case CACHED:
                    b.startReturn().string("this").end();
                    break;
            }

            return ex;
        }

        private CodeExecutableElement createCopyConstructor() {
            CodeExecutableElement ex = new CodeExecutableElement(null, this.getSimpleName().toString());
            CodeTreeBuilder b = ex.createBuilder();

            b.startStatement();
            b.startSuperCall();
            for (VariableElement var : ElementFilter.fieldsIn(abstractBytecodeNode.getEnclosedElements())) {
                String name = var.getSimpleName().toString();
                ex.addParameter(new CodeVariableElement(var.asType(), name));
                b.string(name);
            }
            b.end();
            b.end();

            for (VariableElement var : ElementFilter.fieldsIn(this.getEnclosedElements())) {
                if (var.getModifiers().contains(STATIC)) {
                    continue;
                }
                String name = var.getSimpleName().toString();
                ex.addParameter(new CodeVariableElement(var.asType(), name));
                b.statement("this.", name, " = ", name);
            }

            return ex;
        }

        private CodeExecutableElement createUpdate() {
            CodeExecutableElement ex = GeneratorUtils.override(ElementUtils.findInstanceMethod(abstractBytecodeNode, "update", null));
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("assert bytecodes_ != null || sourceInfo_ != null");

            for (VariableElement e : ElementFilter.fieldsIn(abstractBytecodeNode.getEnclosedElements())) {
                if (e.getModifiers().contains(STATIC)) {
                    continue;
                }
                b.declaration(e.asType(), e.getSimpleName().toString() + "__");
            }

            b.startIf().string("bytecodes_ != null").end().startBlock();
            if (model.isBytecodeUpdatable()) {
                b.statement("bytecodes__ = bytecodes_");
                b.statement("constants__ = constants_");
                b.statement("handlers__ = handlers_");
                b.statement("numNodes__ = numNodes_");
                b.statement("locals__ = locals_");

                if (model.enableTagInstrumentation) {
                    b.statement("tagRoot__ = tagRoot_");
                }

            } else {
                b.tree(GeneratorUtils.createShouldNotReachHere("The bytecode is not updatable for this node."));
            }
            b.end().startElseBlock();
            b.statement("bytecodes__ = this.bytecodes");
            b.statement("constants__ = this.constants");
            b.statement("handlers__ = this.handlers");
            b.statement("numNodes__ = this.numNodes");
            b.statement("locals__ = this.locals");

            if (model.enableTagInstrumentation) {
                b.statement("tagRoot__ = this.tagRoot");
            }

            b.end();

            b.startIf().string("sourceInfo_ != null").end().startBlock();
            b.statement("sourceInfo__ = sourceInfo_");
            b.statement("sources__ = sources_");
            b.end().startElseBlock();
            b.statement("sourceInfo__ = this.sourceInfo");
            b.statement("sources__ = this.sources");
            b.end();

            if (tier.isCached()) {
                b.startIf().string("bytecodes_ != null").end().startBlock();
                b.lineComment("Can't reuse profile if bytecodes are changed.");
                b.startReturn();
                b.startNew(this.asType());
                for (VariableElement e : ElementFilter.fieldsIn(abstractBytecodeNode.getEnclosedElements())) {
                    if (e.getModifiers().contains(STATIC)) {
                        continue;
                    }
                    b.string(e.getSimpleName().toString() + "__");
                }
                if (model.usesBoxingElimination()) {
                    b.string("this.localTags_.length");
                }
                b.end();
                b.end();
                b.end().startElseBlock();
                /**
                 * NOTE: When we reuse cached nodes, they get adopted *without* invalidation. Code
                 * that relies on the identity of the BytecodeNode parent (e.g., source location
                 * computations) should *not* be on compiled code paths and instead be placed behind
                 * a boundary.
                 */
                b.lineComment("Can reuse profile if bytecodes are unchanged.");
                b.startReturn();
                b.startNew(this.asType());
                for (VariableElement e : ElementFilter.fieldsIn(abstractBytecodeNode.getEnclosedElements())) {
                    if (e.getModifiers().contains(STATIC)) {
                        continue;
                    }
                    b.string(e.getSimpleName().toString() + "__");
                }
                for (VariableElement e : ElementFilter.fieldsIn(this.getEnclosedElements())) {
                    if (e.getModifiers().contains(STATIC)) {
                        continue;
                    }
                    b.string("this.", e.getSimpleName().toString());
                }
                b.end();
                b.end();

            } else {
                b.startReturn();
                b.startNew(this.asType());
                for (VariableElement e : ElementFilter.fieldsIn(abstractBytecodeNode.getEnclosedElements())) {
                    b.string(e.getSimpleName().toString() + "__");
                }
                for (VariableElement e : ElementFilter.fieldsIn(this.getEnclosedElements())) {
                    b.string("this.", e.getSimpleName().toString());
                }
                b.end();
                b.end();
            }

            b.end(); // else
            return ex;
        }

        private CodeExecutableElement createGetTier() {
            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "getTier");
            CodeTreeBuilder b = ex.createBuilder();
            switch (tier) {
                case UNCACHED:
                case UNINITIALIZED:
                    b.startReturn().staticReference(types.BytecodeTier, "UNCACHED").end();
                    break;
                case CACHED:
                    b.startReturn().staticReference(types.BytecodeTier, "CACHED").end();
                    break;
            }

            return ex;
        }

        private CodeExecutableElement createFindBytecodeIndexOfOperationNode() {
            CodeExecutableElement ex = new CodeExecutableElement(type(int.class), "findBytecodeIndexOfOperationNode");
            ex.addParameter(new CodeVariableElement(types.Node, "operationNode"));

            CodeTreeBuilder b = ex.createBuilder();
            if (!tier.isCached()) {
                b.startReturn().string("-1").end();
                return ex;
            }

            boolean hasNodeImmediate = false;
            for (InstructionModel instr : model.getInstructions()) {
                if (instr.hasNodeImmediate()) {
                    hasNodeImmediate = true;
                    break;
                }
            }
            if (!hasNodeImmediate) {
                b.lineComment("No operation node exposed.");
                b.startReturn().string("-1").end();
                return ex;
            }

            b.startAssert().string("operationNode.getParent() == this : ").doubleQuote("Passed node must be an operation node of the same bytecode node.").end();
            b.declaration(arrayOf(types.Node), "localNodes", "this.cachedNodes_");
            b.declaration(arrayOf(type(byte.class)), "bc", "this.bytecodes");
            b.statement("int bci = 0");
            b.string("loop: ").startWhile().string("bci < bc.length").end().startBlock();
            b.declaration(type(int.class), "currentBci", "bci");
            b.declaration(type(int.class), "nodeIndex");
            b.startSwitch().tree(readInstruction("bc", "bci")).end().startBlock();

            Map<Boolean, List<InstructionModel>> instructionsGroupedByHasNode = model.getInstructions().stream().collect(Collectors.partitioningBy(InstructionModel::hasNodeImmediate));
            Map<Integer, List<InstructionModel>> nodelessGroupedByLength = instructionsGroupedByHasNode.get(false).stream().collect(
                            deterministicGroupingBy(InstructionModel::getInstructionLength));

            record LengthAndNodeIndex(int length, int nodeIndex) {
            }
            Map<LengthAndNodeIndex, List<InstructionModel>> nodedGroupedByLengthAndNodeIndex = instructionsGroupedByHasNode.get(true).stream() //
                            .collect(deterministicGroupingBy(insn -> new LengthAndNodeIndex(insn.getInstructionLength(), insn.getImmediate(ImmediateKind.NODE_PROFILE).offset())));

            // Skip the nodeless instructions. We group them by size to simplify the generated code.
            for (Map.Entry<Integer, List<InstructionModel>> entry : nodelessGroupedByLength.entrySet()) {
                for (InstructionModel instr : entry.getValue()) {
                    b.startCase().tree(createInstructionConstant(instr)).end();
                }
                b.startBlock();
                b.statement("bci += " + entry.getKey());
                b.statement("continue loop");
                b.end();
            }

            // For each noded instruction, read its node index and continue after the switch.
            // We group them by size and node index to simplify the generated code.
            for (Map.Entry<LengthAndNodeIndex, List<InstructionModel>> entry : nodedGroupedByLengthAndNodeIndex.entrySet()) {
                for (InstructionModel instr : entry.getValue()) {
                    b.startCase().tree(createInstructionConstant(instr)).end();
                }
                InstructionModel representativeInstruction = entry.getValue().get(0);
                InstructionImmediate imm = representativeInstruction.getImmediate(ImmediateKind.NODE_PROFILE);
                b.startBlock();

                b.startStatement().string("nodeIndex = ");
                b.tree(readImmediate("bc", "bci", imm));
                b.end();

                b.statement("bci += " + representativeInstruction.getInstructionLength());
                b.statement("break");
                b.end();
            }

            b.caseDefault().startBlock();
            emitThrowAssertionError(b, "\"Should not reach here\"");
            b.end();

            b.end(); // } switch

            // nodeIndex is guaranteed to be set, since we continue to the top of the loop when
            // there's
            // no node.
            b.startIf().string("localNodes[nodeIndex] == operationNode").end().startBlock();
            b.startReturn().string("currentBci").end();
            b.end();

            b.end(); // } while

            // Fallback: the node wasn't found.
            b.startReturn().string("-1").end();

            return withTruffleBoundary(ex);

        }

        private CodeExecutableElement createToString() {
            CodeExecutableElement ex = GeneratorUtils.override(context.getDeclaredType(Object.class), "toString");
            CodeTreeBuilder b = ex.createBuilder();
            String tierString = switch (tier) {
                case UNCACHED -> "uncached";
                case UNINITIALIZED -> "uninitialized";
                case CACHED -> "cached";
            };

            b.startReturn();
            b.startStaticCall(type(String.class), "format");
            b.doubleQuote(ElementUtils.getSimpleName(types.BytecodeNode) + " [name=%s, sources=%s, tier=" + tierString + "]");
            b.string("((RootNode) getParent()).getQualifiedName()");
            b.string("this.sourceInfo != null");
            b.end(2);

            return ex;
        }

        private CodeExecutableElement createUncachedConstructor() {
            CodeExecutableElement ex = GeneratorUtils.createConstructorUsingFields(Set.of(), this);
            CodeTreeBuilder b = ex.appendBuilder();
            if (model.defaultUncachedThresholdExpression.resolveConstant() != null) {
                if (!(model.defaultUncachedThresholdExpression.resolveConstant() instanceof Integer i) || i < 0) {
                    // The parser should have validated the type. The expression grammar should not
                    // support negative literals like "-42".
                    throw new AssertionError();
                }
                b.statement("this.uncachedExecuteCount_ = ", model.defaultUncachedThreshold);
            } else {
                // Constant needs to be validated at run time.
                b.startStatement().startCall("setUncachedThreshold").string(model.defaultUncachedThreshold).end(2);
            }
            return ex;
        }

        private CodeExecutableElement createCachedConstructor() {

            record CachedInitializationKey(int instructionLength, List<InstructionImmediate> immediates, String nodeName, boolean separateYield) implements Comparable<CachedInitializationKey> {
                CachedInitializationKey(InstructionModel instr, BytecodeDSLModel m) {
                    this(instr.getInstructionLength(),
                                    instr.getImmediates().stream().filter((i) -> needsCachedInitialization(instr, i)).toList(),
                                    cachedDataClassName(instr),
                                    // We need to allocate a stable tag assumption if the node has
                                    // continuations.
                                    m.usesBoxingElimination() && instr.kind == InstructionKind.YIELD);
                }

                @Override
                public int compareTo(CachedInitializationKey o) {
                    // Put a separate yield at the end.
                    int compare = Boolean.compare(this.separateYield, o.separateYield);
                    if (compare != 0) {
                        return compare;
                    }
                    // Order by # of immediates to initialize.
                    compare = Integer.compare(this.immediates.size(), o.immediates.size());
                    if (compare != 0) {
                        return compare;
                    }
                    // Order by immediate kind.
                    for (int i = 0; i < this.immediates.size(); i++) {
                        ImmediateKind thisKind = this.immediates.get(i).kind();
                        ImmediateKind otherKind = o.immediates.get(i).kind();
                        compare = thisKind.compareTo(otherKind);
                        if (compare != 0) {
                            return compare;
                        }
                    }
                    // Order by length.
                    compare = Integer.compare(this.instructionLength, o.instructionLength);
                    if (compare != 0) {
                        return compare;
                    }
                    return 0;
                }
            }

            CodeExecutableElement ex = GeneratorUtils.createConstructorUsingFields(Set.of(), this);
            if (model.usesBoxingElimination()) {
                // The cached node needs numLocals to allocate the tags array (below).
                ex.addParameter(new CodeVariableElement(type(int.class), "numLocals"));
            }

            TypeMirror nodeArrayType = new ArrayCodeTypeMirror(types.Node);

            CodeTreeBuilder b = ex.appendBuilder();

            b.tree(createNeverPartOfCompilation());
            b.declaration(nodeArrayType, "result", "new Node[this.numNodes]");
            b.statement("byte[] bc = bytecodes");
            b.statement("int bci = 0");
            b.statement("int numConditionalBranches = 0");
            if (model.usesBoxingElimination() && model.enableYield) {
                b.statement("boolean hasContinuations = false");
            }

            b.string("loop: ").startWhile().string("bci < bc.length").end().startBlock();
            b.startSwitch().tree(readInstruction("bc", "bci")).end().startBlock();

            Map<CachedInitializationKey, List<InstructionModel>> grouped = model.getInstructions().stream()//
                            .filter((i -> !i.isQuickening())) //
                            .collect(deterministicGroupingBy(i -> new CachedInitializationKey(i, model)));
            List<CachedInitializationKey> sortedKeys = grouped.keySet().stream().sorted().toList();

            for (CachedInitializationKey key : sortedKeys) {
                List<InstructionModel> instructions = grouped.get(key);
                for (InstructionModel instr : instructions) {
                    b.startCase().tree(createInstructionConstant(instr)).end();
                    for (InstructionModel quick : instr.getFlattenedQuickenedInstructions()) {
                        b.startCase().tree(createInstructionConstant(quick)).end();
                    }
                }

                b.startCaseBlock();
                for (InstructionImmediate immediate : key.immediates()) {
                    switch (immediate.kind()) {
                        case BRANCH_PROFILE:
                            b.statement("numConditionalBranches++");
                            break;
                        case NODE_PROFILE:
                            b.startStatement().string("result[");
                            b.tree(readImmediate("bc", "bci", immediate)).string("] = ");
                            b.string("insert(new " + key.nodeName() + "())");
                            b.end();
                            break;
                        default:
                            break;
                    }
                }

                if (key.separateYield) {
                    if (!model.usesBoxingElimination() || !model.enableYield) {
                        throw new AssertionError();
                    }
                    b.statement("hasContinuations = true");
                }

                b.statement("bci += " + key.instructionLength());
                b.statement("break");
                b.end();
            }

            b.caseDefault().startBlock();
            emitThrowAssertionError(b, "\"Should not reach here\"");
            b.end();

            b.end(); // } switch
            b.end(); // } while

            b.startAssert().string("bci == bc.length").end();
            b.startAssign("this.cachedNodes_").string("result").end();
            b.startAssign("this.branchProfiles_").startCall("allocateBranchProfiles").string("numConditionalBranches").end(2);
            b.startAssign("this.exceptionProfiles_").string("handlers.length == 0 ? EMPTY_EXCEPTION_PROFILES : new boolean[handlers.length / 5]").end();

            if (model.epilogExceptional != null) {
                b.startAssign("this.epilogExceptionalNode_").startCall("insert").startNew(getCachedDataClassType(model.epilogExceptional.operation.instruction)).end().end().end();
            }

            if (model.usesBoxingElimination()) {
                b.declaration(type(byte[].class), "localTags", "new byte[numLocals]");
                b.statement("Arrays.fill(localTags, FrameSlotKind.Illegal.tag)");
                b.startAssign("this.localTags_").string("localTags").end();
                if (model.enableYield) {
                    b.startAssign("this.stableTagsAssumption_");
                    b.string("hasContinuations ? ");
                    b.startStaticCall(types.Assumption, "create").doubleQuote("Stable local tags").end();
                    b.string(" : null");
                    b.end();
                }
            }

            this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(boolean[].class), "EMPTY_EXCEPTION_PROFILES")).createInitBuilder().string("new boolean[0]");
            return ex;
        }

        private CodeExecutableElement createSetUncachedThreshold() {
            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "setUncachedThreshold", new String[]{"threshold"}, new TypeMirror[]{type(int.class)});
            ElementUtils.setVisibility(ex.getModifiers(), PUBLIC);
            ex.getModifiers().remove(ABSTRACT);

            CodeTreeBuilder b = ex.createBuilder();
            if (tier.isUncached()) {
                b.tree(createNeverPartOfCompilation());
                b.startIf().string("threshold < 0 && threshold != ", FORCE_UNCACHED_THRESHOLD).end().startBlock();
                emitThrowIllegalArgumentException(b, "threshold cannot be a negative value other than " + FORCE_UNCACHED_THRESHOLD);
                b.end();
                b.startAssign("uncachedExecuteCount_").string("threshold").end();
            } else {
                // do nothing for cached
            }
            return ex;
        }

        private List<CodeExecutableElement> createContinueAt() {
            // This method returns a list containing the continueAt method plus helper methods for
            // custom instructions. The helper methods help reduce the bytecode size of the dispatch
            // loop.
            List<CodeExecutableElement> methods = new ArrayList<>();

            CodeExecutableElement ex = new CodeExecutableElement(Set.of(FINAL), type(long.class), "continueAt");
            GeneratorUtils.addOverride(ex);
            ex.addAnnotationMirror(new CodeAnnotationMirror(types.HostCompilerDirectives_BytecodeInterpreterSwitch));
            ex.addParameter(new CodeVariableElement(BytecodeRootNodeElement.this.asType(), "$root"));
            ex.addParameter(new CodeVariableElement(types.VirtualFrame, "frame_"));
            if (model.enableYield) {
                ex.addParameter(new CodeVariableElement(types.VirtualFrame, "localFrame_"));
            }
            ex.addParameter(new CodeVariableElement(type(long.class), "startState"));

            methods.add(ex);

            CodeTreeBuilder b = ex.createBuilder();
            if (tier.isUninitialized()) {
                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                b.statement("$root.transitionToCached()");
                b.startReturn().string("startState").end();
                return methods;
            }

            b.startDeclaration(types.VirtualFrame, "frame").startCall("ACCESS.uncheckedCast").string("frame_").string("FRAME_TYPE").end().end();
            if (model.enableYield) {
                b.startDeclaration(types.VirtualFrame, "localFrame").startCall("ACCESS.uncheckedCast").string("localFrame_").string("FRAME_TYPE").end().end();
            }

            if (tier.isUncached()) {
                b.startDeclaration(types.EncapsulatingNodeReference, "encapsulatingNode").startStaticCall(types.EncapsulatingNodeReference, "getCurrent").end().end();
                b.startDeclaration(types.Node, "prev").startCall("encapsulatingNode", "set").string("this").end().end();
                b.startTryBlock();

                b.startIf().string("uncachedExecuteCount_ <= 1").end().startBlock();
                b.startIf().string("uncachedExecuteCount_ != " + FORCE_UNCACHED_THRESHOLD).end().startBlock();
                b.statement("$root.transitionToCached(frame, 0)");
                b.startReturn().string("startState").end();
                b.end(2);
                b.startElseBlock();
                b.statement("uncachedExecuteCount_--");
                b.end();
            }

            b.declaration(arrayOf(type(byte.class)), "bc", "this.bytecodes");
            b.declaration(arrayOf(type(Object.class)), "consts", "this.constants");

            if (tier.isCached()) {
                b.declaration(arrayOf(types.Node), "cachedNodes", "this.cachedNodes_");
                b.declaration(arrayOf(type(int.class)), "branchProfiles", "this.branchProfiles_");
                ex.addAnnotationMirror(createExplodeLoopAnnotation("MERGE_EXPLODE"));

                if (model.usesBoxingElimination()) {
                    b.declaration(arrayOf(type(byte.class)), "localTags", "this.localTags_");
                }
            }

            b.statement("int bci = ", decodeBci("startState"));
            b.statement("int sp = ", decodeSp("startState"));
            b.statement("int op");
            b.statement("long temp");

            if (tier.isCached()) {
                b.declaration(loopCounter.asType(), "loopCounter", CodeTreeBuilder.createBuilder().startNew(loopCounter.asType()).end());
            }
            if (model.needsBciSlot() && !model.storeBciInFrame && !tier.isUncached()) {
                // If a bci slot is allocated but not used for non-uncached interpreters, set it to
                // an invalid value just in case it gets read during a stack walk.
                b.statement("FRAMES.setInt(" + localFrame() + ", " + BCI_INDEX + ", -1)");
            }

            b.string("loop: ").startWhile().string("true").end().startBlock();
            b.startStatement().startStaticCall(types.CompilerAsserts, "partialEvaluationConstant").string("bci").end().end();

            // filtered instructions
            List<InstructionModel> instructions = model.getInstructions().stream().//
                            filter((i) -> !tier.isUncached() || !i.isQuickening()).//
                            filter((i) -> isInstructionReachable(i)).//
                            toList();

            List<List<InstructionModel>> instructionPartitions = partitionInstructions(instructions);
            b.startAssign("op").tree(readInstruction("bc", "bci")).end();

            b.startStatement().startStaticCall(types.CompilerAsserts, "partialEvaluationConstant").string("op").end().end();
            if (model.overridesBytecodeDebugListenerMethod("beforeInstructionExecute")) {
                b.startStatement();
                b.startCall("$root.beforeInstructionExecute");
                emitParseInstruction(b, "this", "bci", CodeTreeBuilder.singleString("op"));
                b.end().end();
            }

            b.startTryBlock();
            b.startSwitch().string("op").end().startBlock();

            List<InstructionModel> topLevelInstructions = instructionPartitions.get(0);
            Map<Boolean, List<InstructionModel>> groupedInstructions = topLevelInstructions.stream().collect(deterministicGroupingBy((i) -> isForceCached(tier, i)));
            List<InstructionModel> forceCachedInstructions = groupedInstructions.getOrDefault(Boolean.TRUE, List.of());
            List<InstructionModel> otherTopLevelInstructions = groupedInstructions.getOrDefault(Boolean.FALSE, List.of());

            for (InstructionModel instruction : otherTopLevelInstructions) {
                buildInstructionCaseBlock(b, instruction);
            }

            if (!forceCachedInstructions.isEmpty()) {
                if (!tier.isUncached()) {
                    throw new AssertionError();
                }
                for (InstructionModel forceCachedInstruction : forceCachedInstructions) {
                    buildInstructionCases(b, forceCachedInstruction);
                }
                b.startBlock();
                b.statement("$root.transitionToCached(frame, bci)");
                b.statement("return ", encodeState("bci", "sp"));
                b.end();
            }

            if (instructionPartitions.size() > 1) {
                Map<InstructionGroup, Integer> groupIndices = new LinkedHashMap<>();
                Map<InstructionGroup, List<InstructionModel>> instructionGroups = new LinkedHashMap<>();
                AtomicInteger index = new AtomicInteger();
                CodeExecutableElement firstContinueAt = null;
                for (int partitionIndex = 1; partitionIndex < instructionPartitions.size(); partitionIndex++) {
                    for (InstructionModel instruction : instructionPartitions.get(partitionIndex)) {
                        if (isForceCached(tier, instruction)) {
                            throw new AssertionError("Force cached not supported in non top-level partion.");
                        }
                        InstructionGroup group = new InstructionGroup(instruction);
                        groupIndices.computeIfAbsent(group, (k) -> index.incrementAndGet());
                        instructionGroups.computeIfAbsent(group, (k) -> new ArrayList<>()).add(instruction);
                    }
                    boolean hasMorePartitions = (partitionIndex + 1) < instructionPartitions.size();
                    CodeExecutableElement continueAt = createPartitionContinueAt(partitionIndex, instructionPartitions.get(partitionIndex), groupIndices, hasMorePartitions);
                    methods.add(continueAt);
                    if (firstContinueAt == null) {
                        firstContinueAt = continueAt;
                    }
                }

                b.caseDefault().startCaseBlock();
                b.lineComment("Due to a limit of " + JAVA_JIT_BYTECODE_LIMIT + " bytecodes for Java JIT compilation");
                b.lineComment("we delegate further bytecodes into a separate method.");
                b.startSwitch().startCall(firstContinueAt.getSimpleName().toString());
                for (VariableElement var : firstContinueAt.getParameters()) {
                    b.string(var.getSimpleName().toString());
                }
                b.end().end().startBlock();
                for (var entry : groupIndices.entrySet()) {
                    InstructionGroup group = entry.getKey();
                    int groupIndex = entry.getValue();
                    b.startCase().string(groupIndex).end().startCaseBlock();
                    emitCustomStackEffect(b, group.stackEffect());
                    b.statement("bci += " + group.instructionLength());
                    b.statement("break");
                    b.end(); // case block
                }
                b.end(); // switch block
                b.statement("break");
                b.end(); // default case block

            }

            b.end(); // switch
            if (model.overridesBytecodeDebugListenerMethod("afterInstructionExecute")) {
                b.startStatement();
                b.startCall("$root.afterInstructionExecute");
                emitParseInstruction(b, "this", "bci", CodeTreeBuilder.singleString("op"));
                b.string("null");
                b.end().end();
            }
            b.end(); // try

            b.startCatchBlock(type(Throwable.class), "throwable");
            storeBciInFrameIfNecessary(b);

            if (model.overridesBytecodeDebugListenerMethod("afterInstructionExecute")) {
                b.startStatement();
                b.startCall("$root.afterInstructionExecute");
                emitParseInstruction(b, "this", "bci", CodeTreeBuilder.singleString("op"));
                b.string("throwable");
                b.end().end();
            }
            /*
             * Three kinds of exceptions are supported: AbstractTruffleException,
             * ControlFlowException, and internal error (anything else). All of these can be
             * intercepted by user-provided hooks.
             *
             * The interception order is ControlFlowException -> internal error ->
             * AbstractTruffleException. An intercept method can produce a new exception that can be
             * intercepted by a subsequent intercept method.
             */
            if (model.interceptControlFlowException != null) {
                b.startIf().string("throwable instanceof ").type(types.ControlFlowException).end().startBlock();
                b.startTryBlock();
                b.startAssign("temp");
                b.startCall("resolveControlFlowException");
                b.string("$root").string(localFrame()).string("bci").startGroup().cast(types.ControlFlowException).string("throwable").end();
                b.end().end(); // call, return

                emitBeforeReturnProfiling(b);

                b.statement("return temp");

                b.end().startCatchBlock(types.ControlFlowException, "rethrownCfe");
                b.startThrow().string("rethrownCfe").end();
                b.end().startCatchBlock(types.AbstractTruffleException, "t");
                b.statement("throwable = t");
                b.end().startCatchBlock(type(Throwable.class), "t");
                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                b.statement("throwable = t");
                b.end();
                b.end(); // if
                b.startAssign("throwable").string("resolveThrowable($root, " + localFrame() + ", bci, throwable)").end();
            } else {
                b.startAssign("throwable").string("resolveThrowable($root, " + localFrame() + ", bci, throwable)").end();
            }

            b.startAssign("op").string("-EXCEPTION_HANDLER_LENGTH").end();
            b.startWhile().string("(op = resolveHandler(bci, op + EXCEPTION_HANDLER_LENGTH, this.handlers)) != -1").end().startBlock();

            boolean hasSpecialHandler = model.enableTagInstrumentation || model.epilogExceptional != null;

            if (hasSpecialHandler) {
                b.startTryBlock();
                b.startSwitch().string("this.handlers[op + EXCEPTION_HANDLER_OFFSET_KIND]").end().startBlock();
                if (model.epilogExceptional != null) {
                    b.startCase().string("HANDLER_EPILOG_EXCEPTIONAL").end().startCaseBlock();
                    b.startIf().string("throwable instanceof ").type(type(ThreadDeath.class)).end().startBlock();
                    b.statement("continue");
                    b.end();
                    b.startStatement().startCall("doEpilogExceptional");
                    b.string("$root").string("frame");
                    if (model.enableYield) {
                        b.string("localFrame");
                    }
                    b.string("bc").string("bci").string("sp");
                    b.startGroup().cast(types.AbstractTruffleException);
                    b.string("throwable");
                    b.end();
                    b.string("this.handlers[op + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI]");
                    b.end().end();
                    b.statement("throw sneakyThrow(throwable)");
                    b.end();
                }
                if (model.enableTagInstrumentation) {
                    b.startCase().string("HANDLER_TAG_EXCEPTIONAL").end().startCaseBlock();
                    b.declaration(tagNode.asType(), "node", "this.tagRoot.tagNodes[this.handlers[op + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI]]");
                    b.statement("Object result = doTagExceptional(frame, node, this.handlers[op + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI], bc, bci, throwable)");

                    b.startIf().string("result == null").end().startBlock();
                    b.startThrow().string("throwable").end();
                    b.end();
                    b.statement("temp = this.handlers[op + EXCEPTION_HANDLER_OFFSET_HANDLER_SP] + $root.maxLocals");

                    b.startIf().string("result == ").staticReference(types.ProbeNode, "UNWIND_ACTION_REENTER").end().startBlock();
                    b.lineComment("Reenter by jumping to the begin bci.");
                    b.statement("bci = node.enterBci");
                    b.end().startElseBlock();

                    b.startSwitch().string("readValidBytecode(bc, node.returnBci)").end().startBlock();
                    for (var entry : model.getInstructions().stream().filter((i) -> i.kind == InstructionKind.TAG_LEAVE).collect(deterministicGroupingBy((i) -> {
                        if (i.isReturnTypeQuickening()) {
                            return i.signature.returnType;
                        } else {
                            return type(Object.class);
                        }
                    })).entrySet()) {
                        int length = -1;
                        for (InstructionModel instruction : entry.getValue()) {
                            b.startCase().tree(createInstructionConstant(instruction)).end();
                            if (length != -1 && instruction.getInstructionLength() != length) {
                                throw new AssertionError("Unexpected length.");
                            }
                            length = instruction.getInstructionLength();
                        }
                        TypeMirror targetType = entry.getKey();
                        b.startCaseBlock();

                        CodeExecutableElement expectMethod = null;
                        if (!ElementUtils.isObject(targetType)) {
                            expectMethod = lookupExpectMethod(parserType, targetType);
                            b.startTryBlock();
                        }

                        b.startStatement();
                        startSetFrame(b, targetType).string("frame").string("(int)temp");
                        if (expectMethod == null) {
                            b.string("result");
                        } else {
                            b.startStaticCall(expectMethod);
                            b.string("result");
                            b.end();
                        }
                        b.end(); // setFrame
                        b.end(); // statement

                        if (!ElementUtils.isObject(targetType)) {
                            b.end().startCatchBlock(types.UnexpectedResultException, "e");
                            b.startStatement();
                            startSetFrame(b, type(Object.class)).string("frame").string("(int)temp").string("e.getResult()").end();
                            b.end(); // statement
                            b.end(); // catch
                        }

                        b.statement("temp = temp + 1");
                        b.statement("bci = node.returnBci + " + length);

                        b.statement("break");
                        b.end();
                    }
                    for (InstructionModel instruction : model.getInstructions().stream().filter((i) -> i.kind == InstructionKind.TAG_LEAVE_VOID).toList()) {
                        b.startCase().tree(createInstructionConstant(instruction)).end();
                        b.startCaseBlock();
                        b.statement("bci = node.returnBci + " + instruction.getInstructionLength());
                        b.lineComment("discard return value");
                        b.statement("break");
                        b.end();
                    }
                    b.caseDefault().startCaseBlock();
                    b.tree(GeneratorUtils.createShouldNotReachHere());
                    b.end(); // case default
                    b.end(); // switch
                    b.end();

                    b.statement("break");
                    b.end();
                }

                b.caseDefault().startCaseBlock();
            }
            b.startIf().string("throwable instanceof ").type(type(ThreadDeath.class)).end().startBlock();
            b.statement("continue");
            b.end();
            b.startAssert().string("throwable instanceof ").type(types.AbstractTruffleException).end();
            b.statement("bci = this.handlers[op + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI]");
            b.statement("temp = this.handlers[op + EXCEPTION_HANDLER_OFFSET_HANDLER_SP] + $root.maxLocals");
            b.statement(setFrameObject("((int) temp) - 1", "throwable"));

            if (hasSpecialHandler) {
                b.statement("break");
                b.end(); // case block
                b.end(); // switch
                b.end(); // try
                b.startCatchBlock(type(Throwable.class), "t");
                b.startIf().string("t != throwable").end().startBlock();
                b.statement("throwable = resolveThrowable($root, " + localFrame() + ", bci, t)");
                b.end();
                b.statement("continue");
                b.end();
            }

            /**
             * handlerSp - 1 is the sp before pushing the exception. The current sp should be at or
             * above this height.
             */
            b.statement("assert sp >= temp - 1");
            b.startWhile().string("sp > temp").end().startBlock();
            b.statement(clearFrame("frame", "--sp"));
            b.end();
            b.statement("sp = (int) temp");
            b.statement("continue loop");

            b.end(); // while

            /**
             * NB: Reporting here ensures loop counts are reported before a guest-language exception
             * bubbles up. Loop counts may be lost when host exceptions are thrown (a compromise to
             * avoid complicating the generated code too much).
             */
            emitBeforeReturnProfiling(b);
            if (model.overridesBytecodeDebugListenerMethod("afterRootExecute")) {
                b.startStatement();
                b.startCall("$root.afterRootExecute");
                emitParseInstruction(b, "this", "bci", CodeTreeBuilder.singleString("op"));
                b.string("null");
                b.string("throwable");
                b.end();
                b.end();
            }
            b.statement("throw sneakyThrow(throwable)");

            b.end(); // catch

            b.end(); // while (true)

            if (tier.isUncached()) {
                b.end().startFinallyBlock();
                b.startStatement();
                b.startCall("encapsulatingNode", "set").string("prev").end();
                b.end();
                b.end();
            }

            methods.addAll(doInstructionMethods.values());
            return methods;
        }

        private CodeExecutableElement createPartitionContinueAt(int partitionIndex, List<InstructionModel> instructionGroup, Map<InstructionGroup, Integer> groupIndices, boolean hasMorePartitions) {
            String methodName = "continueAt_" + partitionIndex;
            CodeExecutableElement continueAtMethod = new CodeExecutableElement(Set.of(PRIVATE), type(int.class), methodName);

            continueAtMethod.getAnnotationMirrors().add(new CodeAnnotationMirror(types.HostCompilerDirectives_BytecodeInterpreterSwitch));

            continueAtMethod.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));
            if (model.enableYield) {
                continueAtMethod.getParameters().add(new CodeVariableElement(types.VirtualFrame, "localFrame"));
            }

            List<CodeVariableElement> extraParams = createExtraParameters(instructionGroup.stream().anyMatch(i -> i.hasImmediate(ImmediateKind.CONSTANT)));
            if (tier.isCached()) {
                continueAtMethod.getParameters().add(new CodeVariableElement(new ArrayCodeTypeMirror(types.Node), "cachedNodes"));
            }
            continueAtMethod.getParameters().addAll(extraParams);
            continueAtMethod.addParameter(new CodeVariableElement(type(int.class), "op"));

            CodeTreeBuilder b = continueAtMethod.createBuilder();

            b.startSwitch().string("op").end().startBlock();
            for (InstructionModel instruction : instructionGroup) {
                int groupIndex = groupIndices.get(new InstructionGroup(instruction));

                buildInstructionCases(b, instruction);
                b.startCaseBlock();
                buildCustomInstructionExecute(b, instruction);
                b.startReturn().string(groupIndex).end();
                b.end();
            }
            if (hasMorePartitions) {
                b.caseDefault().startCaseBlock();
                b.startReturn();
                b.startCall("continueAt_" + (partitionIndex + 1));
                for (VariableElement var : continueAtMethod.getParameters()) {
                    b.string(var.getSimpleName().toString());
                }
                b.end();
                b.end();
                b.end();
            }
            b.end(); // switch block

            if (!hasMorePartitions) {
                b.returnDefault();
            }
            return continueAtMethod;
        }

        private void buildInstructionCases(CodeTreeBuilder b, InstructionModel instruction) {
            b.startCase().tree(createInstructionConstant(instruction)).end();
            if (tier.isUncached()) {
                for (InstructionModel quickendInstruction : instruction.getFlattenedQuickenedInstructions()) {
                    b.startCase().tree(createInstructionConstant(quickendInstruction)).end();
                }
            }
        }

        private void buildInstructionCaseBlock(CodeTreeBuilder b, InstructionModel instr) {
            buildInstructionCases(b, instr);
            b.startBlock();

            switch (instr.kind) {
                case BRANCH:
                    b.statement("bci = " + readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.BYTECODE_INDEX)));
                    b.statement("break");
                    break;
                case BRANCH_BACKWARD:
                    if (tier.isUncached()) {
                        b.statement("bci = " + readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.BYTECODE_INDEX)));

                        b.startIf().string("uncachedExecuteCount_ <= 1").end().startBlock();
                        /*
                         * The force uncached check is put in here so that we don't need to check it
                         * in the common case (the else branch where we just decrement).
                         */
                        b.startIf().string("uncachedExecuteCount_ != ", FORCE_UNCACHED_THRESHOLD).end().startBlock();
                        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                        b.statement("$root.transitionToCached(frame, bci)");
                        b.statement("return ", encodeState("bci", "sp"));
                        b.end(2);
                        b.startElseBlock();
                        b.statement("uncachedExecuteCount_--");
                        b.end();
                    } else {
                        emitReportLoopCount(b, CodeTreeBuilder.createBuilder().string("++loopCounter.value >= ").staticReference(loopCounter.asType(), "REPORT_LOOP_STRIDE").build(), true);

                        b.startAssign("temp");
                        b.startCall(lookupBranchBackward(instr).getSimpleName().toString());
                        b.string("frame");
                        if (model.enableYield) {
                            b.string("localFrame");
                        }
                        b.string("bc").string("bci").string("sp");
                        b.end();
                        b.end();

                        b.startIf().string("temp != -1").end().startBlock();
                        b.statement("return temp");
                        b.end();
                        b.statement("bci = " + readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.BYTECODE_INDEX)));
                    }
                    b.statement("break");
                    break;
                case BRANCH_FALSE:
                    String booleanValue = "(boolean) " + uncheckedGetFrameObject("sp - 1");
                    b.startIf();
                    if (tier.isUncached()) {
                        b.string(booleanValue);
                    } else {
                        b.startCall("profileBranch");
                        b.string("branchProfiles");
                        b.tree(readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.BRANCH_PROFILE)));
                        if (model.isBoxingEliminated(type(boolean.class))) {
                            if (instr.isQuickening()) {
                                b.startCall(lookupDoBranch(instr).getSimpleName().toString());
                                if (model.bytecodeDebugListener) {
                                    b.string("this");
                                }
                                b.string("frame").string("bc").string("bci").string("sp");
                                b.end();
                            } else {
                                b.startCall(lookupDoSpecializeBranch(instr).getSimpleName().toString());
                                if (model.bytecodeDebugListener) {
                                    b.string("this");
                                }
                                b.string("frame").string("bc").string("bci").string("sp");
                                b.end();
                            }
                        } else {
                            b.string(booleanValue);
                        }
                        b.end();
                    }
                    b.end(); // if

                    b.startBlock();
                    b.statement("sp -= 1");
                    b.statement("bci += " + instr.getInstructionLength());
                    b.statement("break");
                    b.end().startElseBlock();
                    b.statement("sp -= 1");
                    b.statement("bci = " + readImmediate("bc", "bci", instr.getImmediate("branch_target")));
                    b.statement("break");
                    b.end();
                    break;
                case CUSTOM_SHORT_CIRCUIT:
                    ShortCircuitInstructionModel shortCircuitInstruction = instr.shortCircuitModel;

                    b.startIf();

                    if (tier.isCached()) {
                        b.startCall("profileBranch");
                        b.string("branchProfiles");
                        b.tree(readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.BRANCH_PROFILE)));
                        b.startGroup();
                    }

                    if (shortCircuitInstruction.continueWhen()) {
                        b.string("!");
                    }
                    b.string("(boolean) ").string(uncheckedGetFrameObject("sp - 1"));

                    if (tier.isCached()) {
                        b.end(2); // profileBranch call
                    }

                    b.end().startBlock();
                    /*
                     * NB: Short circuit operations can evaluate to an operand or to the boolean
                     * conversion of an operand. The stack is different in either case.
                     */
                    if (shortCircuitInstruction.producesBoolean()) {
                        // Stack: [..., convertedValue]
                        // leave convertedValue on the top of stack
                    } else {
                        // Stack: [..., value, convertedValue]
                        // pop convertedValue
                        b.statement(clearFrame("frame", "sp - 1"));
                        b.statement("sp -= 1");
                    }
                    b.startAssign("bci").tree(readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.BYTECODE_INDEX))).end();
                    b.statement("break");
                    b.end().startElseBlock();
                    if (shortCircuitInstruction.producesBoolean()) {
                        // Stack: [..., convertedValue]
                        // clear convertedValue
                        b.statement(clearFrame("frame", "sp - 1"));
                        b.statement("sp -= 1");
                    } else {
                        // Stack: [..., value, convertedValue]
                        // clear convertedValue and value
                        b.statement(clearFrame("frame", "sp - 1"));
                        b.statement(clearFrame("frame", "sp - 2"));
                        b.statement("sp -= 2");
                    }
                    b.statement("bci += " + instr.getInstructionLength());
                    b.statement("break");
                    b.end();
                    break;
                case TAG_RESUME:
                    b.startStatement();
                    b.startCall(lookupTagResume(instr).getSimpleName().toString());
                    b.string("frame");
                    b.string("bc").string("bci").string("sp");
                    b.end();
                    b.end();
                    break;
                case TAG_ENTER:
                    b.startStatement();
                    b.startCall(lookupTagEnter(instr).getSimpleName().toString());
                    b.string("frame");
                    b.string("bc").string("bci").string("sp");
                    b.end();
                    b.end();
                    break;
                case TAG_YIELD:
                    b.startStatement();
                    b.startCall(lookupTagYield(instr).getSimpleName().toString());
                    b.string("frame");
                    b.string("bc").string("bci").string("sp");
                    b.end();
                    b.end();
                    break;
                case TAG_LEAVE:
                    if (tier.isUncached() || instr.isQuickening() || !model.usesBoxingElimination()) {
                        b.startStatement();
                        b.startCall(lookupTagLeave(instr).getSimpleName().toString());
                        if (model.bytecodeDebugListener) {
                            b.string("this");
                        }
                        b.string("frame");
                        b.string("bc").string("bci").string("sp");
                        b.end();
                        b.end();
                    } else {
                        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                        b.startStatement();
                        b.startCall(lookupSpecializeTagLeave(instr).getSimpleName().toString());
                        if (model.bytecodeDebugListener) {
                            b.string("this");
                        }
                        b.string("frame");
                        b.string("bc").string("bci").string("sp");
                        b.end();
                        b.end();
                    }
                    break;
                case TAG_LEAVE_VOID:
                    b.startStatement();
                    b.startCall(lookupTagLeaveVoid(instr).getSimpleName().toString());
                    b.string("frame");
                    b.string("bc").string("bci").string("sp");
                    b.end();
                    b.end();
                    break;
                case LOAD_ARGUMENT:
                    if (instr.isReturnTypeQuickening()) {
                        b.startStatement();
                        b.startCall(lookupLoadArgument(instr).getSimpleName().toString());
                        b.string("frame");
                        if (model.enableYield) {
                            b.string("localFrame");
                        }
                        b.string("bc").string("bci").string("sp");
                        b.end();
                        b.end();
                    } else {
                        InstructionImmediate argIndex = instr.getImmediate(ImmediateKind.SHORT);
                        b.startStatement();
                        startSetFrame(b, type(Object.class)).string("frame").string("sp");
                        b.startGroup();
                        b.string(localFrame() + ".getArguments()[" + readImmediate("bc", "bci", argIndex).toString() + "]");
                        b.end(); // argument group
                        b.end(); // set frame
                        b.end(); // statement
                    }

                    b.statement("sp += 1");
                    break;
                case LOAD_CONSTANT:
                    InstructionImmediate constIndex = instr.getImmediate(ImmediateKind.CONSTANT);
                    TypeMirror returnType = instr.signature.returnType;
                    if (tier.isUncached() || (model.usesBoxingElimination() && !ElementUtils.isObject(returnType))) {
                        b.startStatement();
                        startSetFrame(b, returnType).string("frame").string("sp");
                        b.tree(readConstFastPath(readImmediate("bc", "bci", constIndex), returnType));
                        b.end();
                        b.end();
                    } else {
                        b.startIf().startStaticCall(types.CompilerDirectives, "inCompiledCode").end(2).startBlock();
                        b.statement("loadConstantCompiled(frame, bc, bci, sp, consts)");
                        b.end().startElseBlock();
                        b.statement(setFrameObject("sp", readConstFastPath(readImmediate("bc", "bci", constIndex)).toString()));
                        b.end();
                    }
                    b.statement("sp += 1");
                    break;
                case LOAD_NULL:
                    b.startStatement();
                    startSetFrame(b, type(Object.class)).string("frame").string("sp");
                    b.string("null");
                    b.end();
                    b.end();
                    b.statement("sp += 1");
                    break;
                case LOAD_EXCEPTION:
                    InstructionImmediate exceptionSp = instr.getImmediate(ImmediateKind.STACK_POINTER);
                    b.startStatement();
                    startSetFrame(b, type(Object.class)).string("frame").string("sp");
                    startGetFrameUnsafe(b, "frame", type(Object.class)).startGroup().string("$root.maxLocals + ").tree(readImmediate("bc", "bci", exceptionSp)).end(2);
                    b.end(); // set frame
                    b.end(); // statement
                    b.statement("sp += 1");
                    break;
                case POP:
                    if (instr.isQuickening() || tier.isUncached() || !model.usesBoxingElimination()) {
                        b.startStatement();
                        b.startCall(lookupDoPop(instr).getSimpleName().toString());
                        if (model.bytecodeDebugListener) {
                            b.string("this");
                        }
                        b.string("frame");
                        b.string("bc").string("bci").string("sp");
                        b.end();
                        b.end();
                    } else {
                        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                        b.startStatement();
                        b.startCall(lookupDoSpecializePop(instr).getSimpleName().toString());
                        if (model.bytecodeDebugListener) {
                            b.string("this");
                        }
                        b.string("frame");
                        b.string("bc").string("bci").string("sp");
                        b.end();
                        b.end();
                    }
                    b.statement("sp -= 1");
                    break;
                case DUP:
                    b.statement(copyFrameSlot("sp - 1", "sp"));
                    b.statement("sp += 1");
                    break;
                case RETURN:
                    storeBciInFrameIfNecessary(b);
                    emitBeforeReturnProfiling(b);
                    if (model.overridesBytecodeDebugListenerMethod("afterRootExecute")) {
                        b.startStatement();
                        b.startCall("$root.afterRootExecute");
                        emitParseInstruction(b, "this", "bci", CodeTreeBuilder.singleString("op"));
                        startGetFrameUnsafe(b, "frame", type(Object.class)).string("(sp - 1)");
                        b.end();
                        b.string("null");
                        b.end();
                        b.end();
                    }
                    emitReturnTopOfStack(b);
                    break;
                case LOAD_LOCAL:
                    if (instr.isQuickening() || tier.isUncached() || !model.usesBoxingElimination()) {
                        b.startStatement();
                        b.startCall(lookupDoLoadLocal(instr).getSimpleName().toString());
                        if (model.bytecodeDebugListener) {
                            b.string("this");
                        }
                        b.string("frame");
                        if (model.enableYield) {
                            b.string("localFrame");
                        }
                        b.string("bc").string("bci").string("sp");
                        if (localAccessNeedsLocalTags(instr)) {
                            b.string("localTags");
                        }
                        b.end();
                        b.end();
                    } else {
                        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                        b.startStatement();
                        b.startCall(lookupDoSpecializeLoadLocal(instr).getSimpleName().toString());
                        if (model.bytecodeDebugListener) {
                            b.string("this");
                        }
                        b.string("frame");
                        if (model.enableYield) {
                            b.string("localFrame");
                        }
                        b.string("bc").string("bci").string("sp");
                        if (localAccessNeedsLocalTags(instr)) {
                            b.string("localTags");
                        }
                        b.end();
                        b.end();
                    }
                    b.statement("sp += 1");
                    break;
                case LOAD_LOCAL_MATERIALIZED:
                    String materializedFrame = "((VirtualFrame) " + uncheckedGetFrameObject("sp - 1)");
                    if (instr.isQuickening() || tier.isUncached() || !model.usesBoxingElimination()) {
                        b.startStatement();
                        b.startCall(lookupDoLoadLocal(instr).getSimpleName().toString());
                        if (model.bytecodeDebugListener) {
                            b.string("this");
                        }
                        b.string("frame").string(materializedFrame).string("bc").string("bci").string("sp");
                        b.end();
                        b.end();
                    } else {
                        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                        b.startStatement();
                        b.startCall(lookupDoSpecializeLoadLocal(instr).getSimpleName().toString());
                        if (model.bytecodeDebugListener) {
                            b.string("this");
                        }
                        b.string("frame").string(materializedFrame).string("bc").string("bci").string("sp");
                        b.end();
                        b.end();
                    }
                    break;
                case STORE_LOCAL:
                    if (instr.isQuickening() || tier.isUncached() || !model.usesBoxingElimination()) {
                        b.startStatement();
                        b.startCall(lookupDoStoreLocal(instr).getSimpleName().toString());
                        b.string("frame");
                        if (model.enableYield) {
                            b.string("localFrame");
                        }
                        b.string("bc").string("bci").string("sp");
                        if (localAccessNeedsLocalTags(instr)) {
                            b.string("localTags");
                        }
                        b.end();
                        b.end();
                    } else {
                        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                        b.startStatement();
                        b.startCall(lookupDoSpecializeStoreLocal(instr).getSimpleName().toString());
                        b.string("frame");
                        if (model.enableYield) {
                            b.string("localFrame");
                        }
                        b.string("bc").string("bci").string("sp");
                        startRequireFrame(b, type(Object.class)).string("frame").string("sp - 1").end();
                        if (localAccessNeedsLocalTags(instr)) {
                            b.string("localTags");
                        }
                        b.end();
                        b.end();
                    }
                    b.statement(clearFrame("frame", "sp - 1"));
                    b.statement("sp -= 1");
                    break;
                case STORE_LOCAL_MATERIALIZED:
                    materializedFrame = "((VirtualFrame) " + uncheckedGetFrameObject("sp - 2)");

                    if (instr.isQuickening() || tier.isUncached() || !model.usesBoxingElimination()) {
                        b.startStatement();
                        b.startCall(lookupDoStoreLocal(instr).getSimpleName().toString());
                        b.string("frame").string(materializedFrame).string("bc").string("bci").string("sp");
                        b.end();
                        b.end();
                    } else {
                        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                        b.startStatement();
                        b.startCall(lookupDoSpecializeStoreLocal(instr).getSimpleName().toString());
                        b.string("frame").string(materializedFrame).string("bc").string("bci").string("sp");
                        startRequireFrame(b, type(Object.class)).string(localFrame()).string("sp - 1").end();
                        b.end();
                        b.end();
                    }

                    b.statement("sp -= 2");
                    break;
                case MERGE_CONDITIONAL:
                    if (!model.usesBoxingElimination()) {
                        throw new AssertionError("Merge.conditional only supports boxing elimination enabled.");
                    }
                    if (instr.isQuickening() || tier.isUncached() || !model.usesBoxingElimination()) {
                        b.startStatement();
                        b.startCall(lookupDoMergeConditional(instr).getSimpleName().toString());
                        if (model.bytecodeDebugListener) {
                            b.string("this");
                        }
                        b.string("frame").string("bc").string("bci").string("sp");
                        b.end();
                        b.end();
                    } else {
                        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                        b.startStatement();
                        b.startCall(lookupDoSpecializeMergeConditional(instr).getSimpleName().toString());
                        if (model.bytecodeDebugListener) {
                            b.string("this");
                        }
                        b.string("frame").string("bc").string("bci").string("sp");
                        startRequireFrame(b, type(Object.class)).string("frame").string("sp - 1").end();
                        b.end();
                        b.end();
                    }
                    b.statement("sp -= 1");
                    break;
                case THROW:
                    b.statement("throw sneakyThrow((Throwable) " + uncheckedGetFrameObject("frame", "sp - 1") + ")");
                    break;
                case YIELD:
                    storeBciInFrameIfNecessary(b);
                    emitBeforeReturnProfiling(b);

                    if (model.overridesBytecodeDebugListenerMethod("afterRootExecute")) {
                        b.startStatement();
                        b.startCall("$root.afterRootExecute");
                        emitParseInstruction(b, "this", "bci", CodeTreeBuilder.singleString("op"));
                        startGetFrameUnsafe(b, "frame", type(Object.class)).string("(sp - 1)");
                        b.end();
                        b.string("null");
                        b.end();
                        b.end();
                    }
                    b.startStatement();
                    b.startCall(lookupYield(instr).getSimpleName().toString());
                    b.string("frame");
                    if (model.enableYield) {
                        b.string("localFrame");
                    }
                    b.string("bc").string("bci").string("sp").string("$root").string("consts");
                    b.end();
                    b.end();

                    emitReturnTopOfStack(b);
                    break;
                case STORE_NULL:
                    b.statement(setFrameObject("sp", "null"));
                    b.statement("sp += 1");
                    break;
                case CLEAR_LOCAL:
                    String index = readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.FRAME_INDEX)).toString();
                    if (model.defaultLocalValueExpression != null) {
                        b.statement(setFrameObject("frame", index, "DEFAULT_LOCAL_VALUE"));
                    } else {
                        b.statement(clearFrame("frame", index));
                    }
                    break;
                case LOAD_VARIADIC:
                    int effect = -instr.variadicPopCount + 1;
                    b.startStatement();
                    if (instr.variadicPopCount == 0) {
                        b.string(setFrameObject("sp", BytecodeRootNodeElement.this.emptyObjectArray.getSimpleName().toString()));
                    } else {
                        b.string(setFrameObject("sp - " + instr.variadicPopCount, "readVariadic(frame, sp, " + instr.variadicPopCount + ")"));
                    }
                    b.end();

                    if (effect != 0) {
                        if (effect > 0) {
                            b.statement("sp += " + effect);
                        } else {
                            b.statement("sp -= " + -effect);
                        }
                    }
                    break;
                case MERGE_VARIADIC:
                    b.statement(setFrameObject("sp - 1", "mergeVariadic((Object[]) " + uncheckedGetFrameObject("sp - 1") + ")"));
                    break;
                case CUSTOM:
                    if (tier.isUncached() && instr.operation.customModel.forcesCached()) {
                        throw new AssertionError("forceCached instructions should be emitted separately");
                    }
                    buildCustomInstructionExecute(b, instr);
                    emitCustomStackEffect(b, getStackEffect(instr));
                    break;
                case SUPERINSTRUCTION:
                    // not implemented yet
                    break;
                case INVALIDATE:
                    emitInvalidate(b);
                    break;
                default:
                    throw new UnsupportedOperationException("not implemented: " + instr.kind);
            }
            if (!instr.isControlFlow()) {
                b.statement("bci += " + instr.getInstructionLength());
                b.statement("break");
            }
            b.end();
        }

        record InstructionGroup(int stackEffect, int instructionLength) {
            InstructionGroup(InstructionModel instr) {
                this(getStackEffect(instr), instr.getInstructionLength());
            }
        }

        /**
         * Unfortunately HotSpot does not JIT methods bigger than {@link #JAVA_JIT_BYTECODE_LIMIT}
         * bytecodes. So we need to split up the instructions.
         */
        private List<List<InstructionModel>> partitionInstructions(List<InstructionModel> originalInstructions) {
            int instructionCount = originalInstructions.size();
            int estimatedSize = ESTIMATED_BYTECODE_FOOTPRINT + (instructionCount * ESTIMATED_CUSTOM_INSTRUCTION_SIZE);

            if (estimatedSize > JAVA_JIT_BYTECODE_LIMIT) {
                List<InstructionModel> topLevelInstructions = new ArrayList<>();
                List<InstructionModel> partitionableInstructions = new ArrayList<>();
                for (InstructionModel instruction : originalInstructions) {
                    if (instruction.kind != InstructionKind.CUSTOM || isForceCached(tier, instruction)) {
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
                return List.of(originalInstructions);
            }
        }

        private static boolean isForceCached(InterpreterTier tier, InstructionModel instruction) {
            return tier.isUncached() && instruction.kind == InstructionKind.CUSTOM && instruction.operation.customModel.forcesCached();
        }

        private static boolean isInstructionReachable(InstructionModel model) {
            return !model.isEpilogExceptional();
        }

        private void emitInvalidate(CodeTreeBuilder b) {
            if (tier.isCached()) {
                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
            }
            b.startReturn().string(encodeState("bci", "sp")).end();
        }

        private CodeExecutableElement createResolveControlFlowException() {
            CodeExecutableElement method = new CodeExecutableElement(
                            Set.of(PRIVATE),
                            type(long.class), "resolveControlFlowException",
                            new CodeVariableElement(BytecodeRootNodeElement.this.asType(), "$root"),
                            new CodeVariableElement(types.VirtualFrame, "frame"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(types.ControlFlowException, "cfe"));

            method.getThrownTypes().add(type(Throwable.class));

            CodeTreeBuilder b = method.createBuilder();
            b.startAssign("Object result").startCall("$root", model.interceptControlFlowException).string("cfe").string("frame").string("this").string("bci").end(2);
            // There may not be room above the sp. Just use the first stack slot.
            b.statement(setFrameObject("$root.maxLocals", "result"));
            b.startDeclaration(type(int.class), "sp").string("$root.maxLocals + 1").end();
            emitReturnTopOfStack(b);
            return method;

        }

        private CodeExecutableElement createResolveThrowable() {
            CodeExecutableElement method = new CodeExecutableElement(
                            Set.of(PRIVATE),
                            type(Throwable.class), "resolveThrowable",
                            new CodeVariableElement(BytecodeRootNodeElement.this.asType(), "$root"),
                            new CodeVariableElement(types.VirtualFrame, "frame"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(Throwable.class), "throwable"));

            method.addAnnotationMirror(new CodeAnnotationMirror(types.HostCompilerDirectives_InliningCutoff));

            CodeTreeBuilder b = method.createBuilder();

            if (model.interceptTruffleException == null) {
                b.startIf().startGroup().string("throwable instanceof ").type(types.AbstractTruffleException).string(" ate").end(2).startBlock();
                b.startReturn().string("ate").end();
                b.end();
            } else {
                b.declaration(types.AbstractTruffleException, "ex");
                b.startIf().startGroup().string("throwable instanceof ").type(types.AbstractTruffleException).string(" ate").end(2).startBlock();
                b.startAssign("ex").string("ate").end();
                b.end();
            }
            b.startElseIf().startGroup().string("throwable instanceof ").type(types.ControlFlowException).string(" cfe").end(2).startBlock();
            b.startThrow().string("cfe").end();
            b.end();
            if (model.enableTagInstrumentation) {
                b.startElseIf().startGroup().string("throwable instanceof ").type(type(ThreadDeath.class)).string(" cfe").end(2).startBlock();
                b.startReturn().string("cfe").end();
                b.end();
            }

            if (model.interceptInternalException == null) {
                // Special case: no handlers for non-Truffle exceptions. Just rethrow.
                b.startElseBlock();
                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                b.startThrow().string("sneakyThrow(throwable)").end();
                b.end();
            } else {
                b.startElseBlock();
                b.startTryBlock();
                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                if (model.interceptInternalException != null) {
                    b.startAssign("throwable").startCall("$root", model.interceptInternalException).string("throwable").string("frame").string("this").string("bci").end(2);
                }
                b.startThrow().startCall("sneakyThrow").string("throwable").end(2);
                b.end().startCatchBlock(types.AbstractTruffleException, "ate");
                if (model.interceptTruffleException == null) {
                    b.startReturn().string("ate").end();
                } else {
                    b.startAssign("ex").string("ate").end();
                }
                b.end();
                b.end();
            }

            if (model.interceptTruffleException != null) {
                b.startReturn().startCall("$root", model.interceptTruffleException).string("ex").string("frame").string("this").string("bci").end(2);
            }

            return method;

        }

        private CodeExecutableElement createResolveHandler() {
            CodeExecutableElement method = new CodeExecutableElement(
                            Set.of(PRIVATE),
                            type(int.class), "resolveHandler",
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(int.class), "handler"),
                            new CodeVariableElement(type(int[].class), "localHandlers"));
            method.addAnnotationMirror(new CodeAnnotationMirror(types.ExplodeLoop));

            if (!tier.isCached()) {
                method.getModifiers().add(STATIC);
            }

            CodeTreeBuilder b = method.createBuilder();

            if (tier.isCached()) {
                b.declaration(type(int.class), "handlerEntryIndex", "Math.floorDiv(handler, EXCEPTION_HANDLER_LENGTH)");
            }
            if (tier.isCached()) {
                b.startFor().string("int i = handler; i < localHandlers.length; i += EXCEPTION_HANDLER_LENGTH, handlerEntryIndex++").end().startBlock();
            } else {
                b.startFor().string("int i = handler; i < localHandlers.length; i += EXCEPTION_HANDLER_LENGTH").end().startBlock();
            }
            b.startIf().string("localHandlers[i + EXCEPTION_HANDLER_OFFSET_START_BCI] > bci").end().startBlock().statement("continue").end();
            b.startIf().string("localHandlers[i + EXCEPTION_HANDLER_OFFSET_END_BCI] <= bci").end().startBlock().statement("continue").end();

            if (tier.isCached()) {
                b.startIf().string("!this.exceptionProfiles_[handlerEntryIndex]").end().startBlock();
                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                b.statement("this.exceptionProfiles_[handlerEntryIndex] = true");
                b.end();
            }

            b.statement("return i");

            b.end();

            b.statement("return -1");
            return method;

        }

        private Collection<List<InstructionModel>> groupInstructionsByKindAndImmediates(InstructionModel.InstructionKind... kinds) {
            return model.getInstructions().stream().filter((i) -> {
                for (InstructionKind kind : kinds) {
                    if (i.kind == kind) {
                        return true;
                    }
                }
                return false;
            }).collect(deterministicGroupingBy((i -> {
                return i.getImmediates();
            }))).values();
        }

        private CodeExecutableElement createDoEpilogExceptional() {
            CodeExecutableElement method = new CodeExecutableElement(
                            Set.of(PRIVATE),
                            type(void.class), "doEpilogExceptional");

            method.addParameter(new CodeVariableElement(BytecodeRootNodeElement.this.asType(), "$root"));
            method.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));
            if (model.enableYield) {
                method.addParameter(new CodeVariableElement(types.VirtualFrame, "localFrame"));
            }
            method.addParameter(new CodeVariableElement(type(byte[].class), "bc"));
            method.addParameter(new CodeVariableElement(type(int.class), "bci"));
            method.addParameter(new CodeVariableElement(type(int.class), "sp"));
            method.addParameter(new CodeVariableElement(types.AbstractTruffleException, "exception"));
            method.addParameter(new CodeVariableElement(type(int.class), "nodeId"));

            CodeTreeBuilder b = method.createBuilder();
            TypeMirror cachedType = getCachedDataClassType(model.epilogExceptional.operation.instruction);
            if (tier.isCached()) {
                b.declaration(cachedType, "node", "this.epilogExceptionalNode_");
            }

            List<CodeVariableElement> extraParams = createExtraParameters(false);
            buildCallExecute(b, model.epilogExceptional.operation.instruction, "exception", extraParams);
            return method;
        }

        private CodeExecutableElement createDoTagExceptional() {
            CodeExecutableElement method = new CodeExecutableElement(
                            Set.of(PRIVATE),
                            type(Object.class), "doTagExceptional",
                            new CodeVariableElement(types.VirtualFrame, "frame"),
                            new CodeVariableElement(tagNode.asType(), "node"),
                            new CodeVariableElement(type(int.class), "nodeId"),
                            new CodeVariableElement(type(byte[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(Throwable.class), "exception"));

            method.getThrownTypes().add(type(Throwable.class));

            Collection<List<InstructionModel>> groupedInstructions = groupInstructionsByKindAndImmediates(InstructionKind.TAG_LEAVE, InstructionKind.TAG_LEAVE_VOID);

            CodeTreeBuilder b = method.createBuilder();
            b.declaration(type(boolean.class), "wasOnReturnExecuted");

            b.startSwitch().string("readValidBytecode(bc, bci)").end().startBlock();
            for (List<InstructionModel> instructions : groupedInstructions) {
                for (InstructionModel instruction : instructions) {
                    b.startCase().tree(createInstructionConstant(instruction)).end();
                }
                b.startCaseBlock();
                InstructionImmediate immediate = model.tagLeaveValueInstruction.getImmediate(ImmediateKind.TAG_NODE);
                b.startAssign("wasOnReturnExecuted").tree(readImmediate("bc", "bci", immediate)).string(" == nodeId").end();
                b.statement("break");
                b.end();
            }
            b.caseDefault().startCaseBlock();
            b.statement("wasOnReturnExecuted = false");
            b.statement("break");
            b.end(); // case default
            b.end(); // switch

            b.statement("return node.findProbe().onReturnExceptionalOrUnwind(frame, exception, wasOnReturnExecuted)");

            return method;

        }

        private CodeExecutableElement lookupTagResume(InstructionModel instr) {
            CodeExecutableElement method = doInstructionMethods.get(instr);
            if (method != null) {
                return method;
            }
            method = new CodeExecutableElement(
                            Set.of(PRIVATE),
                            type(void.class), instructionMethodName(instr),
                            new CodeVariableElement(types.VirtualFrame, "frame"),
                            new CodeVariableElement(type(byte[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(int.class), "sp"));

            method.addAnnotationMirror(new CodeAnnotationMirror(types.HostCompilerDirectives_InliningCutoff));

            CodeTreeBuilder b = method.createBuilder();
            InstructionImmediate imm = instr.getImmediate(ImmediateKind.TAG_NODE);
            b.startDeclaration(tagNode.asType(), "tagNode");
            b.tree(readTagNode(tagNode.asType(), readImmediate("bc", "bci", imm)));
            b.end();
            b.statement("tagNode.findProbe().onResume(frame)");

            doInstructionMethods.put(instr, method);
            return method;

        }

        private CodeExecutableElement lookupTagYield(InstructionModel instr) {
            CodeExecutableElement method = doInstructionMethods.get(instr);
            if (method != null) {
                return method;
            }
            method = new CodeExecutableElement(
                            Set.of(PRIVATE),
                            type(void.class), instructionMethodName(instr),
                            new CodeVariableElement(types.VirtualFrame, "frame"),
                            new CodeVariableElement(type(byte[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(int.class), "sp"));

            method.addAnnotationMirror(new CodeAnnotationMirror(types.HostCompilerDirectives_InliningCutoff));

            CodeTreeBuilder b = method.createBuilder();

            b.startDeclaration(type(Object.class), "returnValue");
            startRequireFrame(b, type(Object.class));
            b.string("frame");
            b.string("sp - 1");
            b.end();
            b.end(); // declaration

            InstructionImmediate imm = instr.getImmediate(ImmediateKind.TAG_NODE);
            b.startDeclaration(tagNode.asType(), "tagNode");
            b.tree(readTagNode(tagNode.asType(), readImmediate("bc", "bci", imm)));
            b.end();
            b.statement("tagNode.findProbe().onYield(frame, returnValue)");

            doInstructionMethods.put(instr, method);
            return method;

        }

        private CodeExecutableElement lookupBranchBackward(InstructionModel instr) {
            CodeExecutableElement method = doInstructionMethods.get(instr);
            if (method != null) {
                return method;
            }
            method = new CodeExecutableElement(
                            Set.of(PRIVATE),
                            type(long.class), instructionMethodName(instr));

            method.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));
            if (model.enableYield) {
                method.addParameter(new CodeVariableElement(types.VirtualFrame, "localFrame"));
            }
            method.addParameter(new CodeVariableElement(type(byte[].class), "bc"));
            method.addParameter(new CodeVariableElement(type(int.class), "bci"));
            method.addParameter(new CodeVariableElement(type(int.class), "sp"));

            CodeTreeBuilder b = method.createBuilder();

            b.startIf().startStaticCall(types.CompilerDirectives, "inInterpreter").end(1).string(" && ") //
                            .startStaticCall(types.BytecodeOSRNode, "pollOSRBackEdge").string("this").end(2).startBlock();

            /**
             * When a while loop is compiled by OSR, its "false" branch profile may be zero, in
             * which case the compiler will stop at loop exits. To coerce the compiler to compile
             * the code after the loop, we encode the branch profile index in the branch.backwards
             * instruction and use it here to force the false profile to a non-zero value.
             */
            InstructionImmediate branchProfile = model.branchBackwardInstruction.findImmediate(ImmediateKind.BRANCH_PROFILE, "loop_header_branch_profile");
            b.declaration(type(int.class), "branchProfileIndex", readImmediate("bc", "bci", branchProfile));
            b.startStatement().startCall("ensureFalseProfile").string("branchProfiles_").string("branchProfileIndex").end(2);

            b.startAssign("Object osrResult");
            b.startStaticCall(types.BytecodeOSRNode, "tryOSR");
            b.string("this");
            String bci = readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.BYTECODE_INDEX)).toString();
            b.string(encodeState(bci, "sp", model.enableYield ? "frame != " + localFrame() : null));
            b.string("null"); // interpreterState
            b.string("null"); // beforeTransfer
            b.string("frame"); // parentFrame
            b.end(2);

            b.startIf().string("osrResult != null").end().startBlock();
            /**
             * executeOSR invokes BytecodeNode#continueAt, which returns a long encoding the sp and
             * bci when it returns/when the bytecode is rewritten. Returning this value is correct
             * in either case: If it's a return, we'll read the result out of the frame (the OSR
             * code copies the OSR frame contents back into our frame first); if it's a rewrite,
             * we'll transition and continue executing.
             */
            b.startReturn().cast(type(long.class)).string("osrResult").end();
            b.end();

            b.end();

            b.statement("return -1");

            doInstructionMethods.put(instr, method);
            return method;

        }

        private CodeExecutableElement lookupLoadArgument(InstructionModel instr) {
            CodeExecutableElement method = doInstructionMethods.get(instr);
            if (method != null) {
                return method;
            }
            method = new CodeExecutableElement(
                            Set.of(PRIVATE),
                            type(void.class), instructionMethodName(instr));

            method.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));
            if (model.enableYield) {
                method.addParameter(new CodeVariableElement(types.VirtualFrame, "localFrame"));
            }
            method.addParameter(new CodeVariableElement(type(byte[].class), "bc"));
            method.addParameter(new CodeVariableElement(type(int.class), "bci"));
            method.addParameter(new CodeVariableElement(type(int.class), "sp"));

            InstructionImmediate argIndex = instr.getImmediate(ImmediateKind.SHORT);

            CodeTreeBuilder b = method.createBuilder();

            TypeMirror returnType = instr.signature.returnType;
            b.startTryBlock();
            b.startStatement();
            startSetFrame(b, returnType).string("frame").string("sp");
            b.startGroup();
            b.startStaticCall(lookupExpectMethod(type(Object.class), returnType));
            b.string(localFrame() + ".getArguments()[" + readImmediate("bc", "bci", argIndex).toString() + "]");
            b.end(); // expect
            b.end(); // argument group
            b.end(); // set frame
            b.end(); // statement
            b.end().startCatchBlock(types.UnexpectedResultException, "e"); // try
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
            emitQuickening(b, "this", "bc", "bci", null,
                            b.create().tree(createInstructionConstant(instr.getQuickeningRoot())).build());
            b.startStatement();
            startSetFrame(b, type(Object.class)).string("frame").string("sp");
            b.string("e.getResult()");
            b.end(); // set frame
            b.end(); // statement
            b.end(); // catch block

            doInstructionMethods.put(instr, method);
            return method;

        }

        private CodeExecutableElement lookupYield(InstructionModel instr) {
            CodeExecutableElement method = doInstructionMethods.get(instr);
            if (method != null) {
                return method;
            }
            method = new CodeExecutableElement(
                            Set.of(PRIVATE),
                            type(void.class), instructionMethodName(instr));

            method.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));
            if (model.enableYield) {
                method.addParameter(new CodeVariableElement(types.VirtualFrame, "localFrame"));
            }
            method.addParameter(new CodeVariableElement(type(byte[].class), "bc"));
            method.addParameter(new CodeVariableElement(type(int.class), "bci"));
            method.addParameter(new CodeVariableElement(type(int.class), "sp"));
            method.addParameter(new CodeVariableElement(BytecodeRootNodeElement.this.asType(), "$root"));
            method.addParameter(new CodeVariableElement(arrayOf(type(Object.class)), "consts"));

            CodeTreeBuilder b = method.createBuilder();

            InstructionImmediate continuationIndex = instr.getImmediate(ImmediateKind.CONSTANT);
            b.statement("int maxLocals = $root.maxLocals");
            b.statement(copyFrameTo("frame", "maxLocals", "localFrame", "maxLocals", "(sp - 1 - maxLocals)"));

            b.startDeclaration(continuationRootNodeImpl.asType(), "continuationRootNode");
            b.tree(readConstFastPath(readImmediate("bc", "bci", continuationIndex), continuationRootNodeImpl.asType()));
            b.end();

            b.startDeclaration(types.ContinuationResult, "continuationResult");
            b.startCall("continuationRootNode.createContinuation");
            b.string(localFrame());
            b.string(uncheckedGetFrameObject("sp - 1"));
            b.end(2);

            b.statement(setFrameObject("sp - 1", "continuationResult"));

            doInstructionMethods.put(instr, method);
            return method;

        }

        private CodeExecutableElement lookupTagEnter(InstructionModel instr) {
            CodeExecutableElement method = doInstructionMethods.get(instr);
            if (method != null) {
                return method;
            }
            method = new CodeExecutableElement(
                            Set.of(PRIVATE),
                            type(void.class), instructionMethodName(instr),
                            new CodeVariableElement(types.VirtualFrame, "frame"),
                            new CodeVariableElement(type(byte[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(int.class), "sp"));

            method.addAnnotationMirror(new CodeAnnotationMirror(types.HostCompilerDirectives_InliningCutoff));

            CodeTreeBuilder b = method.createBuilder();
            InstructionImmediate imm = instr.getImmediate(ImmediateKind.TAG_NODE);
            b.startDeclaration(tagNode.asType(), "tagNode");
            b.tree(readTagNode(tagNode.asType(), readImmediate("bc", "bci", imm)));
            b.end();
            b.statement("tagNode.findProbe().onEnter(frame)");

            doInstructionMethods.put(instr, method);
            return method;

        }

        private CodeExecutableElement lookupTagLeaveVoid(InstructionModel instr) {
            CodeExecutableElement method = doInstructionMethods.get(instr);
            if (method != null) {
                return method;
            }
            method = new CodeExecutableElement(
                            Set.of(PRIVATE),
                            type(void.class), instructionMethodName(instr),
                            new CodeVariableElement(types.VirtualFrame, "frame"),
                            new CodeVariableElement(type(byte[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(int.class), "sp"));

            method.addAnnotationMirror(new CodeAnnotationMirror(types.HostCompilerDirectives_InliningCutoff));

            CodeTreeBuilder b = method.createBuilder();
            InstructionImmediate imm = instr.getImmediate(ImmediateKind.TAG_NODE);
            b.startDeclaration(tagNode.asType(), "tagNode");
            b.tree(readTagNode(tagNode.asType(), readImmediate("bc", "bci", imm)));
            b.end();
            b.statement("tagNode.findProbe().onReturnValue(frame, null)");

            doInstructionMethods.put(instr, method);
            return method;

        }

        private CodeExecutableElement lookupTagLeave(InstructionModel instr) {
            CodeExecutableElement method = doInstructionMethods.get(instr);
            if (method != null) {
                return method;
            }

            method = new CodeExecutableElement(
                            Set.of(PRIVATE),
                            type(void.class), instructionMethodName(instr),
                            new CodeVariableElement(types.VirtualFrame, "frame"),
                            new CodeVariableElement(type(byte[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(int.class), "sp"));

            if (model.bytecodeDebugListener) {
                method.getParameters().add(0, new CodeVariableElement(abstractBytecodeNode.asType(), "$this"));
            }

            CodeTreeBuilder b = method.createBuilder();
            TypeMirror inputType = instr.specializedType == null ? instr.signature.getSpecializedType(0) : instr.specializedType;
            TypeMirror returnType = instr.signature.returnType;

            boolean isSpecialized = instr.specializedType != null;

            b.declaration(inputType, "returnValue");
            if (isSpecialized) {
                b.startTryBlock();
            }
            b.startAssign("returnValue");
            if (isSpecialized) {
                startExpectFrameUnsafe(b, "frame", inputType);
            } else {
                startRequireFrame(b, inputType);
                b.string("frame");
            }
            b.string("sp - 1");
            b.end();
            b.end(); // declaration

            if (isSpecialized) {
                b.end().startCatchBlock(types.UnexpectedResultException, "ex");

                b.startReturn().startCall(lookupSpecializeTagLeave(instr.getQuickeningRoot()).getSimpleName().toString());
                if (model.bytecodeDebugListener) {
                    b.string("$this");
                }
                b.string("frame").string("bc").string("bci").string("sp");
                b.end().end();
            }

            b.end();

            InstructionImmediate imm = instr.getImmediate(ImmediateKind.TAG_NODE);
            b.startDeclaration(tagNode.asType(), "tagNode");
            b.tree(readTagNode(tagNode.asType(), readImmediate("bc", "bci", imm)));
            b.end();
            b.statement("tagNode.findProbe().onReturnValue(frame, returnValue)");

            if (isSpecialized && !ElementUtils.typeEquals(inputType, returnType)) {
                b.startStatement();
                startSetFrame(b, returnType).string("frame").string("sp - 1").string("returnValue").end();
                b.end();
            }

            doInstructionMethods.put(instr, method);
            return method;

        }

        private CodeExecutableElement lookupSpecializeTagLeave(InstructionModel instr) {
            CodeExecutableElement method = doInstructionMethods.get(instr);
            if (method != null) {
                return method;
            }
            method = new CodeExecutableElement(
                            Set.of(PRIVATE),
                            type(void.class), instructionMethodName(instr),
                            new CodeVariableElement(types.VirtualFrame, "frame"),
                            new CodeVariableElement(type(byte[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(int.class), "sp"));

            if (model.bytecodeDebugListener) {
                method.getParameters().add(0, new CodeVariableElement(this.getSuperclass(), "$this"));
            }

            Map<TypeMirror, InstructionModel> typeToSpecialization = new LinkedHashMap<>();
            List<InstructionModel> specializations = instr.quickenedInstructions;
            InstructionModel genericInstruction = null;
            for (InstructionModel specialization : specializations) {
                if (model.isBoxingEliminated(specialization.specializedType)) {
                    typeToSpecialization.put(specialization.specializedType, specialization);
                } else if (specialization.specializedType == null) {
                    genericInstruction = specialization;
                }
            }

            CodeTreeBuilder b = method.createBuilder();
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());

            b.declaration(type(short.class), "newInstruction");
            b.declaration(type(short.class), "newOperand");
            b.declaration(type(int.class), "operandIndex", readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.BYTECODE_INDEX)));
            b.declaration(type(short.class), "operand", readInstruction("bc", "operandIndex"));

            b.startStatement();
            b.type(type(Object.class)).string(" value = ");
            startRequireFrame(b, type(Object.class)).string("frame").string("sp - 1").end();
            b.end();

            boolean elseIf = false;
            for (var entry : typeToSpecialization.entrySet()) {
                TypeMirror typeGroup = entry.getKey();
                elseIf = b.startIf(elseIf);
                b.string("value instanceof ").type(ElementUtils.boxType(typeGroup)).string(" && ");
                b.newLine().string("     (newOperand = ").startCall(createApplyQuickeningName(typeGroup)).string("operand").end().string(") != -1");
                b.end().startBlock();

                InstructionModel specialization = entry.getValue();
                b.startStatement().string("newInstruction = ").tree(createInstructionConstant(specialization)).end();
                b.end(); // else block
                b.end(); // if block
            }

            b.startElseBlock(elseIf);
            b.statement("newOperand = undoQuickening(operand)");
            b.startStatement().string("newInstruction = ").tree(createInstructionConstant(genericInstruction)).end();
            b.end();

            emitQuickeningOperand(b, "$this", "bc", "bci", null, 0, "operandIndex", "operand", "newOperand");
            emitQuickening(b, "$this", "bc", "bci", null, "newInstruction");

            InstructionImmediate imm = instr.getImmediate(ImmediateKind.TAG_NODE);
            b.startDeclaration(tagNode.asType(), "tagNode");
            b.tree(readTagNode(tagNode.asType(), readImmediate("bc", "bci", imm)));
            b.end();
            b.statement("tagNode.findProbe().onReturnValue(frame, value)");

            doInstructionMethods.put(instr, method);
            return method;
        }

        private CodeExecutableElement lookupDoPop(InstructionModel instr) {
            CodeExecutableElement method = doInstructionMethods.get(instr);
            if (method != null) {
                return method;
            }

            method = new CodeExecutableElement(
                            Set.of(PRIVATE, STATIC),
                            type(void.class), instructionMethodName(instr),
                            new CodeVariableElement(types.Frame, "frame"),
                            new CodeVariableElement(type(byte[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(int.class), "sp"));

            if (model.bytecodeDebugListener) {
                method.getParameters().add(0, new CodeVariableElement(abstractBytecodeNode.asType(), "$this"));
            }

            CodeTreeBuilder b = method.createBuilder();
            TypeMirror inputType = instr.signature.getSpecializedType(0);

            boolean isGeneric = ElementUtils.isObject(inputType);

            if (!isGeneric) {
                b.startIf().startStaticCall(types.CompilerDirectives, "inCompiledCode").end().end().startBlock();
                b.lineComment("Always clear in compiled code for liveness analysis");
                b.statement(clearFrame("frame", "sp - 1"));
                b.returnDefault();
                b.end();

                b.startIf().string("frame.getTag(sp - 1) != ").staticReference(frameTagsElement.get(inputType)).end().startBlock();
                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                b.startStatement().startCall(lookupDoSpecializeBranch(instr.getQuickeningRoot()).getSimpleName().toString());
                if (model.bytecodeDebugListener) {
                    b.string("$this");
                }
                b.string("frame").string("bc").string("bci").string("sp");
                b.end().end();
                b.returnDefault();
                b.end();
            }

            if (isGeneric) {
                b.statement(clearFrame("frame", "sp - 1"));
            } else {
                b.lineComment("No need to clear for primitives in the interpreter");
            }

            doInstructionMethods.put(instr, method);
            return method;

        }

        private CodeExecutableElement lookupDoSpecializePop(InstructionModel instr) {
            CodeExecutableElement method = doInstructionMethods.get(instr);
            if (method != null) {
                return method;
            }

            method = new CodeExecutableElement(
                            Set.of(PRIVATE, STATIC),
                            type(void.class), instructionMethodName(instr),
                            new CodeVariableElement(types.Frame, "frame"),
                            new CodeVariableElement(type(byte[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(int.class), "sp"));

            if (model.bytecodeDebugListener) {
                method.getParameters().add(0, new CodeVariableElement(this.getSuperclass(), "$this"));
            }

            Map<TypeMirror, InstructionModel> typeToSpecialization = new LinkedHashMap<>();
            List<InstructionModel> specializations = instr.quickenedInstructions;
            InstructionModel genericInstruction = null;
            for (InstructionModel specialization : specializations) {
                if (model.isBoxingEliminated(specialization.specializedType)) {
                    typeToSpecialization.put(specialization.specializedType, specialization);
                } else if (specialization.specializedType == null) {
                    genericInstruction = specialization;
                }
            }

            CodeTreeBuilder b = method.createBuilder();
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());

            b.declaration(type(short.class), "newInstruction");
            b.declaration(type(int.class), "operandIndex", readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.BYTECODE_INDEX)));

            // Pop may not have a valid child bci.
            b.startIf().string("operandIndex != -1").end().startBlock();

            b.declaration(type(short.class), "newOperand");
            b.declaration(type(short.class), "operand", readInstruction("bc", "operandIndex"));
            b.startStatement();
            b.type(type(Object.class)).string(" value = ");
            startRequireFrame(b, type(Object.class)).string("frame").string("sp - 1").end();
            b.end();

            boolean elseIf = false;
            for (var entry : typeToSpecialization.entrySet()) {
                TypeMirror typeGroup = entry.getKey();
                elseIf = b.startIf(elseIf);
                b.string("value instanceof ").type(ElementUtils.boxType(typeGroup)).string(" && ");
                b.newLine().string("     (newOperand = ").startCall(createApplyQuickeningName(typeGroup)).string("operand").end().string(") != -1");
                b.end().startBlock();

                InstructionModel specialization = entry.getValue();
                b.startStatement().string("newInstruction = ").tree(createInstructionConstant(specialization)).end();
                b.end(); // if block
            }

            b.startElseBlock(elseIf);
            b.statement("newOperand = undoQuickening(operand)");
            b.startStatement().string("newInstruction = ").tree(createInstructionConstant(genericInstruction)).end();
            b.end();

            emitQuickeningOperand(b, "$this", "bc", "bci", null, 0, "operandIndex", "operand", "newOperand");

            b.end(); // case operandIndex != -1
            b.startElseBlock();
            b.startStatement().string("newInstruction = ").tree(createInstructionConstant(genericInstruction)).end();
            b.end(); // case operandIndex == -1

            emitQuickening(b, "$this", "bc", "bci", null, "newInstruction");
            b.statement(clearFrame("frame", "sp - 1"));

            doInstructionMethods.put(instr, method);
            return method;
        }

        private CodeExecutableElement lookupDoBranch(InstructionModel instr) {
            CodeExecutableElement method = doInstructionMethods.get(instr);
            if (method != null) {
                return method;
            }

            method = new CodeExecutableElement(
                            Set.of(PRIVATE, STATIC),
                            type(boolean.class), instructionMethodName(instr),
                            new CodeVariableElement(types.Frame, "frame"),
                            new CodeVariableElement(type(byte[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(int.class), "sp"));

            if (model.bytecodeDebugListener) {
                method.getParameters().add(0, new CodeVariableElement(abstractBytecodeNode.asType(), "$this"));
            }

            CodeTreeBuilder b = method.createBuilder();
            TypeMirror inputType = instr.signature.getSpecializedType(0);

            b.startTryBlock();
            b.startReturn();
            if (ElementUtils.isObject(inputType)) {
                b.string("(boolean) ");
            }
            startExpectFrameUnsafe(b, "frame", inputType);
            b.string("sp - 1");
            b.end();
            b.end(); // statement

            b.end().startCatchBlock(types.UnexpectedResultException, "ex");

            b.startReturn().startCall(lookupDoSpecializeBranch(instr.getQuickeningRoot()).getSimpleName().toString());
            if (model.bytecodeDebugListener) {
                b.string("$this");
            }
            b.string("frame").string("bc").string("bci").string("sp");
            b.end().end();

            b.end();

            doInstructionMethods.put(instr, method);
            return method;

        }

        private CodeExecutableElement lookupDoSpecializeBranch(InstructionModel instr) {
            CodeExecutableElement method = doInstructionMethods.get(instr);
            if (method != null) {
                return method;
            }

            method = new CodeExecutableElement(
                            Set.of(PRIVATE, STATIC),
                            type(boolean.class), instructionMethodName(instr),
                            new CodeVariableElement(types.Frame, "frame"),
                            new CodeVariableElement(type(byte[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(int.class), "sp"));

            if (model.bytecodeDebugListener) {
                method.getParameters().add(0, new CodeVariableElement(abstractBytecodeNode.asType(), "$this"));
            }

            TypeMirror boxingType = type(boolean.class);

            if (instr.quickenedInstructions.size() != 2) {
                throw new AssertionError("Unexpected quickening count");
            }

            InstructionModel boxedInstruction = null;
            InstructionModel unboxedInstruction = null;
            for (InstructionModel quickening : instr.getFlattenedQuickenedInstructions()) {
                if (ElementUtils.isObject(quickening.signature.getSpecializedType(0))) {
                    boxedInstruction = quickening;
                } else {
                    unboxedInstruction = quickening;
                }
            }

            if (boxedInstruction == null || unboxedInstruction == null) {
                throw new AssertionError("Unexpected quickenings");
            }

            CodeTreeBuilder b = method.createBuilder();
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());

            b.startStatement().string("boolean value = (boolean)");
            startRequireFrame(b, type(Object.class));
            b.string("frame").string("sp - 1");
            b.end();
            b.end(); // statement

            b.declaration(type(short.class), "newInstruction");
            b.declaration(type(short.class), "newOperand");
            b.declaration(type(int.class), "operandIndex", readImmediate("bc", "bci", instr.findImmediate(ImmediateKind.BYTECODE_INDEX, "child0")));
            b.declaration(type(short.class), "operand", readInstruction("bc", "operandIndex"));

            b.startIf().string("(newOperand = ").startCall(createApplyQuickeningName(boxingType)).string("operand").end().string(") != -1").end().startBlock();
            b.startStatement().string("newInstruction = ").tree(createInstructionConstant(unboxedInstruction)).end();
            emitOnSpecialize(b, "$this", "bci", readInstruction("bc", "bci"), "BranchFalse$" + unboxedInstruction.getQuickeningName());
            b.end().startElseBlock();
            b.startStatement().string("newInstruction = ").tree(createInstructionConstant(boxedInstruction)).end();
            b.startStatement().string("newOperand = operand").end();
            emitOnSpecialize(b, "$this", "bci", readInstruction("bc", "bci"), "BranchFalse$" + boxedInstruction.getQuickeningName());
            b.end(); // else block

            emitQuickeningOperand(b, "$this", "bc", "bci", null, 0, "operandIndex", "operand", "newOperand");
            emitQuickening(b, "$this", "bc", "bci", null, "newInstruction");

            b.startReturn().string("value").end();

            doInstructionMethods.put(instr, method);
            return method;
        }

        private boolean localAccessNeedsStackFrame(InstructionModel instr) {
            if (!instr.kind.isLocalVariableAccess() && !instr.kind.isLocalVariableMaterializedAccess()) {
                throw new AssertionError();
            }
            return instr.kind.isLocalVariableMaterializedAccess() || model.enableYield;
        }

        private boolean localAccessNeedsLocalTags(InstructionModel instr) {
            // Local tags are only used for cached interpreters with BE. They need to be read
            // separately for materialized accesses, not passed into the method.
            return !instr.kind.isLocalVariableMaterializedAccess() && model.usesBoxingElimination() && tier.isCached();
        }

        private CodeExecutableElement lookupDoLoadLocal(InstructionModel instr) {
            CodeExecutableElement method = doInstructionMethods.get(instr);
            if (method != null) {
                return method;
            }

            method = new CodeExecutableElement(
                            Set.of(PRIVATE),
                            type(void.class), instructionMethodName(instr),
                            new CodeVariableElement(types.Frame, "frame"),
                            new CodeVariableElement(type(byte[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(int.class), "sp"));

            boolean materialized = instr.kind.isLocalVariableMaterializedAccess();
            boolean needsStackFrame = localAccessNeedsStackFrame(instr);
            if (needsStackFrame) {
                method.getParameters().add(0, new CodeVariableElement(types.Frame, "stackFrame"));
            }

            boolean needsLocalTags = localAccessNeedsLocalTags(instr);
            if (needsLocalTags) {
                method.addParameter(new CodeVariableElement(arrayOf(type(byte.class)), "localTags"));
            }

            if (model.bytecodeDebugListener) {
                method.getParameters().add(0, new CodeVariableElement(abstractBytecodeNode.asType(), "$this"));
            }

            final TypeMirror inputType = instr.signature.returnType;
            final TypeMirror slotType = instr.specializedType != null ? instr.specializedType : type(Object.class);

            CodeTreeBuilder b = method.createBuilder();

            CodeTree readSlot = readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.FRAME_INDEX));
            if (materialized) {
                b.declaration(type(int.class), "slot", readSlot);
                b.declaration(type(int.class), "localRootIndex", readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.LOCAL_ROOT)));
                if (instr.hasImmediate(ImmediateKind.LOCAL_INDEX)) {
                    b.declaration(type(int.class), "localIndex", readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.LOCAL_INDEX)));
                }
                emitValidateMaterializedAccess(b, "localRootIndex", "localRoot", null, "localIndex");
                readSlot = CodeTreeBuilder.singleString("slot");
            }

            boolean generic = ElementUtils.typeEquals(type(Object.class), slotType);

            if (!generic) {
                b.startTryBlock();
            }

            b.startStatement();
            startSetFrame(b, inputType).string(needsStackFrame ? "stackFrame" : "frame");
            if (materialized) {
                b.string("sp - 1"); // overwrite the materialized frame
            } else {
                b.string("sp");
            }
            if (generic) {
                startRequireFrame(b, slotType).string("frame").tree(readSlot).end();
            } else {
                startExpectFrameUnsafe(b, "frame", slotType).tree(readSlot).end();
            }
            b.end();
            b.end(); // statement

            if (!generic) {
                if (model.enableBlockScoping) {
                    method.getModifiers().remove(Modifier.STATIC);
                }

                b.end().startCatchBlock(types.UnexpectedResultException, "ex");
                b.startStatement().startCall(lookupDoSpecializeLoadLocal(instr.getQuickeningRoot()).getSimpleName().toString());
                if (model.bytecodeDebugListener) {
                    b.string("$this");
                }
                if (needsStackFrame) {
                    b.string("stackFrame");
                }
                b.string("frame").string("bc").string("bci").string("sp");
                if (needsLocalTags) {
                    b.string("localTags");
                }
                b.end().end();
                b.end();
            }

            doInstructionMethods.put(instr, method);
            return method;

        }

        private CodeExecutableElement lookupDoSpecializeLoadLocal(InstructionModel instr) {
            CodeExecutableElement method = doInstructionMethods.get(instr);
            if (method != null) {
                return method;
            }

            method = new CodeExecutableElement(
                            Set.of(PRIVATE),
                            type(void.class), instructionMethodName(instr),
                            new CodeVariableElement(types.Frame, "frame"),
                            new CodeVariableElement(type(byte[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(int.class), "sp"));

            boolean materialized = instr.kind.isLocalVariableMaterializedAccess();
            boolean needsStackFrame = localAccessNeedsStackFrame(instr);
            if (needsStackFrame) {
                method.getParameters().add(0, new CodeVariableElement(types.Frame, "stackFrame"));
            }

            boolean needsLocalTags = localAccessNeedsLocalTags(instr);
            if (needsLocalTags) {
                method.addParameter(new CodeVariableElement(arrayOf(type(byte.class)), "localTags"));
            }

            if (model.bytecodeDebugListener) {
                method.getParameters().add(0, new CodeVariableElement(abstractBytecodeNode.asType(), "$this"));
            }

            CodeTreeBuilder b = method.createBuilder();

            String stackFrame = needsStackFrame ? "stackFrame" : "frame";
            b.declaration(type(int.class), "slot", readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.FRAME_INDEX)));
            if (materialized) {
                b.declaration(type(int.class), "localRootIndex", readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.LOCAL_ROOT)));
            }
            String localIndex;
            if (model.enableBlockScoping) {
                b.declaration(type(int.class), "localIndex", readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.LOCAL_INDEX)));
                localIndex = "localIndex";
            } else {
                localIndex = "slot - " + USER_LOCALS_START_INDEX;
            }

            String bytecodeNode;
            if (materialized) {
                emitValidateMaterializedAccess(b, "localRootIndex", "localRoot", "bytecodeNode", "localIndex");
                bytecodeNode = "bytecodeNode";
            } else {
                bytecodeNode = "this";
            }

            b.startDeclaration(type(byte.class), "tag");
            b.startCall(bytecodeNode, "getCachedLocalTagInternal");
            if (materialized) {
                b.startCall(bytecodeNode, "getLocalTags").end();
            } else {
                b.string("localTags");
            }
            b.string(localIndex);
            b.end(); // call
            b.end(); // declaration

            b.declaration(type(Object.class), "value");
            b.declaration(type(short.class), "newInstruction");
            InstructionModel genericInstruction = instr.findGenericInstruction();
            b.startTryBlock();

            b.startSwitch().string("tag").end().startBlock();
            for (TypeMirror boxingType : model.boxingEliminatedTypes) {
                InstructionModel boxedInstruction = instr.findSpecializedInstruction(boxingType);

                b.startCase().staticReference(frameTagsElement.get(boxingType)).end();
                b.startCaseBlock();
                b.startStatement().string("newInstruction = ").tree(createInstructionConstant(boxedInstruction)).end();
                emitOnSpecialize(b, "$this", "bci", readInstruction("bc", "bci"), "LoadLocal$" + boxedInstruction.getQuickeningName());
                b.startStatement();
                b.string("value = ");
                startExpectFrameUnsafe(b, "frame", boxingType).string("slot").end();
                b.end();
                b.statement("break");
                b.end();
            }

            b.startCase().staticReference(frameTagsElement.getObject()).end();
            b.startCase().staticReference(frameTagsElement.getIllegal()).end();
            b.startCaseBlock();
            b.startStatement().string("newInstruction = ").tree(createInstructionConstant(genericInstruction)).end();
            emitOnSpecialize(b, "$this", "bci", readInstruction("bc", "bci"), "LoadLocal$" + genericInstruction.getQuickeningName());
            b.startStatement();
            b.string("value = ");
            startExpectFrameUnsafe(b, "frame", type(Object.class)).string("slot").end();
            b.end();
            b.statement("break");
            b.end();

            b.caseDefault().startCaseBlock();
            b.tree(GeneratorUtils.createShouldNotReachHere("Unexpected frame tag."));
            b.end();

            b.end(); // switch

            b.end().startCatchBlock(types.UnexpectedResultException, "ex");

            // If a FrameSlotException occurs, specialize to the generic version.
            b.startStatement().string("newInstruction = ").tree(createInstructionConstant(genericInstruction)).end();
            emitOnSpecialize(b, "$this", "bci", readInstruction("bc", "bci"), "LoadLocal$" + genericInstruction.getQuickeningName());
            b.startStatement();
            b.string("value = ex.getResult()");
            b.end();

            b.end(); // catch

            emitQuickening(b, "$this", "bc", "bci", null, "newInstruction");
            b.startStatement();
            startSetFrame(b, type(Object.class)).string(stackFrame);
            if (materialized) {
                b.string("sp - 1"); // overwrite the materialized frame
            } else {
                b.string("sp");
            }
            b.string("value").end();
            b.end();

            doInstructionMethods.put(instr, method);
            return method;
        }

        private CodeExecutableElement lookupDoMergeConditional(InstructionModel instr) {
            CodeExecutableElement method = doInstructionMethods.get(instr);
            if (method != null) {
                return method;
            }
            method = new CodeExecutableElement(
                            Set.of(PRIVATE, STATIC),
                            type(void.class), instructionMethodName(instr),
                            new CodeVariableElement(types.Frame, "frame"),
                            new CodeVariableElement(type(byte[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(int.class), "sp"));

            if (model.bytecodeDebugListener) {
                method.getParameters().add(0, new CodeVariableElement(abstractBytecodeNode.asType(), "$this"));
            }

            final TypeMirror inputType = instr.signature.getSpecializedType(1);
            final TypeMirror returnType = instr.signature.returnType;

            CodeTreeBuilder b = method.createBuilder();

            if (tier.isCached() && model.usesBoxingElimination()) {
                b.declaration(inputType, "value");
                b.startTryBlock();
                b.startStatement();
                b.string("value = ");
                startExpectFrameUnsafe(b, "frame", inputType).string("sp - 1").end();
                b.end();
                b.end().startCatchBlock(types.UnexpectedResultException, "ex");
                b.startStatement().startCall(lookupDoSpecializeMergeConditional(instr.getQuickeningRoot()).getSimpleName().toString());
                if (model.bytecodeDebugListener) {
                    b.string("$this");
                }
                b.string("frame").string("bc").string("bci").string("sp").string("ex.getResult()");
                b.end().end();

                b.returnDefault();
                b.end(); // catch block
            } else {
                b.startDeclaration(inputType, "value");
                startRequireFrame(b, inputType).string("frame").string("sp - 1").end();
                b.end();
            }

            b.startStatement();
            startSetFrame(b, returnType).string("frame").string("sp - 2").string("value").end();
            b.end();

            if (!ElementUtils.isPrimitive(inputType)) {
                b.statement(clearFrame("frame", "sp - 1"));
            }

            doInstructionMethods.put(instr, method);
            return method;

        }

        private CodeExecutableElement lookupDoSpecializeMergeConditional(InstructionModel instr) {
            CodeExecutableElement method = doInstructionMethods.get(instr);
            if (method != null) {
                return method;
            }
            method = new CodeExecutableElement(
                            Set.of(PRIVATE, STATIC),
                            type(void.class), instructionMethodName(instr),
                            new CodeVariableElement(types.Frame, "frame"),
                            new CodeVariableElement(type(byte[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(int.class), "sp"),
                            new CodeVariableElement(type(Object.class), "local"));

            if (model.bytecodeDebugListener) {
                method.getParameters().add(0, new CodeVariableElement(abstractBytecodeNode.asType(), "$this"));
            }

            CodeTreeBuilder b = method.createBuilder();

            InstructionImmediate operand0 = instr.getImmediates(ImmediateKind.BYTECODE_INDEX).get(0);
            InstructionImmediate operand1 = instr.getImmediates(ImmediateKind.BYTECODE_INDEX).get(1);

            b.startDeclaration(type(boolean.class), "condition");
            b.cast(type(boolean.class));
            startGetFrameUnsafe(b, "frame", null).string("sp - 2");
            b.end().end();

            b.declaration(type(short.class), "newInstruction");
            b.declaration(type(short.class), "newOperand");
            b.declaration(type(short.class), "newOtherOperand");
            b.declaration(type(int.class), "operandIndex");
            b.declaration(type(int.class), "otherOperandIndex");

            b.startIf().string("condition").end().startBlock();
            b.startAssign("operandIndex").tree(readImmediate("bc", "bci", operand0)).end();
            b.startAssign("otherOperandIndex").tree(readImmediate("bc", "bci", operand1)).end();
            b.end().startElseBlock();
            b.startAssign("operandIndex").tree(readImmediate("bc", "bci", operand1)).end();
            b.startAssign("otherOperandIndex").tree(readImmediate("bc", "bci", operand0)).end();
            b.end();

            b.startIf().string("operandIndex != -1 && otherOperandIndex != -1").end().startBlock();

            b.declaration(type(short.class), "operand", readInstruction("bc", "operandIndex"));
            b.declaration(type(short.class), "otherOperand", readInstruction("bc", "otherOperandIndex"));
            InstructionModel genericInstruction = instr.findGenericInstruction();

            boolean elseIf = false;
            for (TypeMirror boxingType : model.boxingEliminatedTypes) {
                elseIf = b.startIf(elseIf);
                b.string("local").instanceOf(ElementUtils.boxType(boxingType));
                b.newLine().string("   && (");
                b.string("(newOperand = ").startCall(createApplyQuickeningName(boxingType)).string("operand").end().string(") != -1)");
                b.end().startBlock();

                InstructionModel boxedInstruction = instr.findSpecializedInstruction(boxingType);
                InstructionModel unboxedInstruction = boxedInstruction.quickenedInstructions.get(0);
                b.startSwitch().tree(readInstruction("bc", "bci")).end().startBlock();
                b.startCase().tree(createInstructionConstant(boxedInstruction.getQuickeningRoot())).end();
                b.startCase().tree(createInstructionConstant(boxedInstruction)).end();
                b.startCaseBlock();
                b.statement("newOtherOperand = otherOperand");
                b.startAssign("newInstruction").tree(createInstructionConstant(boxedInstruction)).end();
                b.statement("break");
                b.end();
                b.startCase().tree(createInstructionConstant(unboxedInstruction)).end();
                b.startCaseBlock();
                b.statement("newOtherOperand = otherOperand");
                b.startAssign("newInstruction").tree(createInstructionConstant(unboxedInstruction)).end();
                b.statement("break");
                b.end();
                b.caseDefault();
                b.startCaseBlock();
                b.statement("newOtherOperand = undoQuickening(otherOperand)");
                b.startAssign("newInstruction").tree(createInstructionConstant(genericInstruction)).end();
                b.statement("break");
                b.end();
                b.end(); // switch

                b.end(); // if block
            }

            b.startElseBlock(elseIf);
            b.statement("newOperand = operand");
            b.statement("newOtherOperand = undoQuickening(otherOperand)");
            b.startAssign("newInstruction").tree(createInstructionConstant(genericInstruction)).end();
            b.end();

            emitQuickeningOperand(b, "$this", "bc", "bci", null, 0, "operandIndex", "operand", "newOperand");
            emitQuickeningOperand(b, "$this", "bc", "bci", null, 0, "otherOperandIndex", "otherOperand", "newOtherOperand");

            b.end(); // case both operand indices are valid
            b.startElseBlock();
            b.startAssign("newInstruction").tree(createInstructionConstant(genericInstruction)).end();
            b.end(); // case either operand index is invalid

            emitQuickening(b, "$this", "bc", "bci", null, "newInstruction");

            b.startStatement();
            startSetFrame(b, type(Object.class)).string("frame").string("sp - 2").string("local").end();
            b.end();
            b.statement(clearFrame("frame", "sp - 1"));

            doInstructionMethods.put(instr, method);
            return method;
        }

        private CodeExecutableElement lookupDoStoreLocal(InstructionModel instr) {
            CodeExecutableElement method = doInstructionMethods.get(instr);
            if (method != null) {
                return method;
            }
            method = new CodeExecutableElement(
                            Set.of(PRIVATE),
                            type(void.class), instructionMethodName(instr),
                            new CodeVariableElement(types.Frame, "frame"),
                            new CodeVariableElement(type(byte[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(int.class), "sp"));

            boolean materialized = instr.kind.isLocalVariableMaterializedAccess();
            boolean needsStackFrame = localAccessNeedsStackFrame(instr);
            if (needsStackFrame) {
                method.getParameters().add(0, new CodeVariableElement(types.Frame, "stackFrame"));
            }

            boolean needsLocalTags = localAccessNeedsLocalTags(instr);
            if (needsLocalTags) {
                method.addParameter(new CodeVariableElement(arrayOf(type(byte.class)), "localTags"));
            }

            final TypeMirror inputType = instr.signature.getSpecializedType(0);
            final TypeMirror slotType = instr.specializedType != null ? instr.specializedType : type(Object.class);

            CodeTreeBuilder b = method.createBuilder();

            String stackFrame = needsStackFrame ? "stackFrame" : "frame";
            if (tier.isCached() && model.usesBoxingElimination()) {
                b.declaration(inputType, "local");
                b.startTryBlock();
                b.startStatement().string("local = ");
                startExpectFrameUnsafe(b, stackFrame, inputType).string("sp - 1").end();
                b.end();

                b.end().startCatchBlock(types.UnexpectedResultException, "ex");
                b.startStatement().startCall(lookupDoSpecializeStoreLocal(instr.getQuickeningRoot()).getSimpleName().toString());
                if (needsStackFrame) {
                    b.string("stackFrame");
                }
                b.string("frame").string("bc").string("bci").string("sp").string("ex.getResult()");
                if (needsLocalTags) {
                    b.string("localTags");
                }
                b.end().end();

                b.returnDefault();
                b.end(); // catch block
            } else {
                b.startDeclaration(inputType, "local");
                startRequireFrame(b, inputType).string(stackFrame).string("sp - 1").end();
                b.end();
            }

            boolean generic = ElementUtils.typeEquals(type(Object.class), inputType);

            CodeTree readSlot = readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.FRAME_INDEX));
            if (generic && !ElementUtils.needsCastTo(inputType, slotType)) {
                if (materialized) {
                    b.declaration(type(int.class), "slot", readSlot);
                    readSlot = CodeTreeBuilder.singleString("slot");
                    b.declaration(type(int.class), "localRootIndex", readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.LOCAL_ROOT)));
                    if (instr.hasImmediate(ImmediateKind.LOCAL_INDEX)) {
                        b.declaration(type(int.class), "localIndex", readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.LOCAL_INDEX)));
                    }

                    if (model.usesBoxingElimination()) {
                        b.declaration(type(int.class), "localOffset", "slot - " + USER_LOCALS_START_INDEX);
                        emitValidateMaterializedAccess(b, "localRootIndex", "localRoot", "bytecodeNode", "localIndex");
                        // We need to update the tags. Call the setter method on the bytecodeNode.
                        b.startStatement().startCall("bytecodeNode.setLocalValueInternal");
                        b.string("frame");
                        b.string("localOffset");
                        if (instr.hasImmediate(ImmediateKind.LOCAL_INDEX)) {
                            b.string("localIndex");
                        } else {
                            b.string("localOffset");
                        }
                        b.string("local");
                        b.end(2);
                    } else {
                        emitValidateMaterializedAccess(b, "localRootIndex", "localRoot", null, "localIndex");
                        b.startStatement();
                        startSetFrame(b, slotType).string("frame").tree(readSlot);
                        b.string("local");
                        b.end();
                        b.end();
                    }

                    b.statement(clearFrame(stackFrame, "sp - 1"));
                    b.statement(clearFrame(stackFrame, "sp - 2"));
                } else {
                    b.startStatement();
                    startSetFrame(b, slotType).string("frame").tree(readSlot);
                    b.string("local");
                    b.end();
                    b.end();
                    b.statement(clearFrame(stackFrame, "sp - 1"));
                }
            } else {
                if (!model.usesBoxingElimination()) {
                    throw new AssertionError("Unexpected path.");
                }

                boolean needsCast = ElementUtils.needsCastTo(inputType, slotType);
                b.declaration(type(int.class), "slot", readSlot);
                if (materialized) {
                    b.declaration(type(int.class), "localRootIndex", readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.LOCAL_ROOT)));
                }
                String localIndex;
                if (model.enableBlockScoping) {
                    b.declaration(type(int.class), "localIndex", readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.LOCAL_INDEX)));
                    localIndex = "localIndex";
                } else {
                    localIndex = "slot - " + USER_LOCALS_START_INDEX;
                }

                String bytecodeNode;
                if (materialized) {
                    emitValidateMaterializedAccess(b, "localRootIndex", "localRoot", "bytecodeNode", "localIndex");
                    bytecodeNode = "bytecodeNode";
                } else {
                    bytecodeNode = "this";
                }

                b.startDeclaration(type(byte.class), "tag");
                b.startCall(bytecodeNode, "getCachedLocalTagInternal");
                if (materialized) {
                    b.startCall(bytecodeNode, "getLocalTags").end();
                } else {
                    b.string("localTags");
                }
                b.string(localIndex);
                b.end(); // call
                b.end(); // declaration

                b.startIf().string("tag == ").staticReference(frameTagsElement.get(slotType));
                b.end().startBlock();
                if (needsCast) {
                    b.startTryBlock();
                }
                b.startStatement();
                startSetFrame(b, slotType).string("frame").string("slot");
                if (needsCast) {
                    b.startStaticCall(lookupExpectMethod(inputType, slotType));
                    b.string("local");
                    b.end();
                } else {
                    b.string("local");
                }
                b.end(); // set frame
                b.end(); // statement

                if (materialized) {
                    b.statement(clearFrame(stackFrame, "sp - 1"));
                    b.startIf().startStaticCall(types.CompilerDirectives, "inCompiledCode").end().end().startBlock();
                    b.lineComment("Clear primitive for compiler liveness analysis");
                    b.statement(clearFrame(stackFrame, "sp - 2"));
                    b.end();
                } else {
                    b.startIf().startStaticCall(types.CompilerDirectives, "inCompiledCode").end().end().startBlock();
                    b.lineComment("Clear primitive for compiler liveness analysis");
                    b.statement(clearFrame(stackFrame, "sp - 1"));
                    b.end();
                }

                b.returnDefault();

                if (needsCast) {
                    b.end().startCatchBlock(types.UnexpectedResultException, "ex");
                    b.statement("local = ex.getResult()");
                    b.lineComment("fall through to slow-path");
                    b.end();  // catch block
                }

                b.end();
                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                b.startStatement().startCall(lookupDoSpecializeStoreLocal(instr.getQuickeningRoot()).getSimpleName().toString());
                if (needsStackFrame) {
                    b.string("stackFrame");
                }
                b.string("frame").string("bc").string("bci").string("sp").string("local");
                if (needsLocalTags) {
                    b.string("localTags");
                }

                b.end().end();
            }

            doInstructionMethods.put(instr, method);
            return method;

        }

        private CodeExecutableElement lookupDoSpecializeStoreLocal(InstructionModel instr) {
            CodeExecutableElement method = doInstructionMethods.get(instr);
            if (method != null) {
                return method;
            }
            method = new CodeExecutableElement(
                            Set.of(PRIVATE),
                            type(void.class), instructionMethodName(instr),
                            new CodeVariableElement(types.Frame, "frame"),
                            new CodeVariableElement(type(byte[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(int.class), "sp"),
                            new CodeVariableElement(type(Object.class), "local"));

            boolean materialized = instr.kind.isLocalVariableMaterializedAccess();
            boolean needsStackFrame = localAccessNeedsStackFrame(instr);
            if (needsStackFrame) {
                method.getParameters().add(0, new CodeVariableElement(types.Frame, "stackFrame"));
            }

            boolean needsLocalTags = localAccessNeedsLocalTags(instr);
            if (needsLocalTags) {
                method.addParameter(new CodeVariableElement(arrayOf(type(byte.class)), "localTags"));
            }

            String stackFrame = needsStackFrame ? "stackFrame" : "frame";

            CodeTreeBuilder b = method.createBuilder();

            b.declaration(type(short.class), "newInstruction");
            b.declaration(type(int.class), "slot", readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.FRAME_INDEX)));
            if (materialized) {
                b.declaration(type(int.class), "localRootIndex", readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.LOCAL_ROOT)));
            }

            String localIndex;
            if (model.enableBlockScoping) {
                b.declaration(type(int.class), "localIndex", readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.LOCAL_INDEX)));
                localIndex = "localIndex";
            } else {
                localIndex = "slot - " + USER_LOCALS_START_INDEX;
            }
            b.declaration(type(int.class), "operandIndex", readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.BYTECODE_INDEX)));

            String bytecodeNode;
            if (materialized) {
                emitValidateMaterializedAccess(b, "localRootIndex", "localRoot", "bytecodeNode", "localIndex");
                bytecodeNode = "bytecodeNode";
            } else {
                bytecodeNode = "this";
            }

            b.declaration(type(short.class), "newOperand");
            b.declaration(type(short.class), "operand", readInstruction("bc", "operandIndex"));

            b.startDeclaration(type(byte.class), "oldTag");
            b.startCall(bytecodeNode, "getCachedLocalTagInternal");
            if (materialized) {
                b.startCall(bytecodeNode, "getLocalTags").end();
            } else {
                b.string("localTags");
            }
            b.string(localIndex);
            b.end(); // call
            b.end(); // declaration
            b.declaration(type(byte.class), "newTag");

            InstructionModel genericInstruction = instr.findGenericInstruction();

            boolean elseIf = false;
            for (TypeMirror boxingType : model.boxingEliminatedTypes) {
                elseIf = b.startIf(elseIf);
                b.string("local").instanceOf(ElementUtils.boxType(boxingType)).end().startBlock();

                // instruction for unsuccessful operand quickening
                InstructionModel boxedInstruction = instr.findSpecializedInstruction(boxingType);
                // instruction for successful operand quickening
                InstructionModel unboxedInstruction = boxedInstruction.quickenedInstructions.get(0);

                b.startSwitch().string("oldTag").end().startBlock();

                b.startCase().staticReference(frameTagsElement.get(boxingType)).end();
                b.startCase().staticReference(frameTagsElement.getIllegal()).end();
                b.startCaseBlock();

                b.startIf().string("(newOperand = ").startCall(createApplyQuickeningName(boxingType)).string("operand").end().string(") != -1").end().startBlock();
                b.startStatement().string("newInstruction = ").tree(createInstructionConstant(unboxedInstruction)).end();
                b.end().startElseBlock();
                b.startStatement().string("newInstruction = ").tree(createInstructionConstant(boxedInstruction)).end();
                b.startStatement().string("newOperand = operand").end();
                b.end(); // else block
                String kindName = ElementUtils.firstLetterUpperCase(ElementUtils.getSimpleName(boxingType));
                emitOnSpecialize(b, "this", "bci", readInstruction("bc", "bci"), "StoreLocal$" + kindName);
                b.startStatement().string("newTag = ").staticReference(frameTagsElement.get(boxingType)).end();
                b.startStatement();
                startSetFrame(b, boxingType).string("frame").string("slot").startGroup().cast(boxingType).string("local").end().end();
                b.end();
                b.statement("break");
                b.end();

                for (TypeMirror otherType : model.boxingEliminatedTypes) {
                    if (ElementUtils.typeEquals(otherType, boxingType)) {
                        continue;
                    }
                    b.startCase().staticReference(frameTagsElement.get(otherType)).end();
                }

                b.startCase().staticReference(frameTagsElement.getObject()).end();
                b.startCaseBlock();
                b.startStatement().string("newInstruction = ").tree(createInstructionConstant(genericInstruction)).end();
                b.startStatement().string("newOperand = ").startCall("undoQuickening").string("operand").end().end();
                b.startStatement().string("newTag = ").staticReference(frameTagsElement.getObject()).end();
                emitOnSpecialize(b, "this", "bci", readInstruction("bc", "bci"), "StoreLocal$" + genericInstruction.getQualifiedQuickeningName());
                b.startStatement();
                startSetFrame(b, type(Object.class)).string("frame").string("slot").string("local").end();
                b.end();
                b.statement("break");
                b.end();

                b.caseDefault().startCaseBlock();
                b.tree(GeneratorUtils.createShouldNotReachHere("Unexpected frame tag."));
                b.end();

                b.end(); // switch
                b.end(); // if block
            }

            b.startElseBlock(elseIf);
            b.startStatement().string("newInstruction = ").tree(createInstructionConstant(genericInstruction)).end();
            b.startStatement().string("newOperand = ").startCall("undoQuickening").string("operand").end().end();
            b.startStatement().string("newTag = ").staticReference(frameTagsElement.getObject()).end();
            emitOnSpecialize(b, "this", "bci", readInstruction("bc", "bci"), "StoreLocal$" + genericInstruction.getQualifiedQuickeningName());
            b.startStatement();
            startSetFrame(b, type(Object.class)).string("frame").string("slot").string("local").end();
            b.end();
            b.end(); // else

            b.startIf().string("newTag != oldTag").end().startBlock();
            b.startStatement().startCall(bytecodeNode, "setCachedLocalTagInternal");
            if (materialized) {
                b.startCall(bytecodeNode, "getLocalTags").end();
            } else {
                b.string("localTags");
            }
            if (model.enableBlockScoping) {
                b.string("localIndex");
            } else {
                b.string("slot - " + USER_LOCALS_START_INDEX);
            }
            b.string("newTag");
            b.end(2);
            b.end(); // if newTag != oldTag

            emitQuickeningOperand(b, "this", "bc", "bci", null, 0, "operandIndex", "operand", "newOperand");

            emitQuickening(b, "this", "bc", "bci", null, "newInstruction");

            b.statement(clearFrame(stackFrame, "sp - 1"));
            if (instr.kind == InstructionKind.STORE_LOCAL_MATERIALIZED) {
                b.statement(clearFrame(stackFrame, "sp - 2"));
            }

            doInstructionMethods.put(instr, method);
            return method;
        }

        /**
         * Helper that emits common validation code for materialized local reads/writes.
         * <p>
         * If {@code localRootVariable} or {@code bytecodeNodeVariable} are provided, declares and
         * initializes locals with those names.
         */
        private void emitValidateMaterializedAccess(CodeTreeBuilder b, String localRootIndex, String localRootVariable, String bytecodeNodeVariable, String localIndex) {
            CodeTree getRoot = CodeTreeBuilder.createBuilder() //
                            .startCall("this.getRoot().getBytecodeRootNodeImpl") //
                            .string(localRootIndex) //
                            .end() //
                            .build();
            if (localRootVariable != null) {
                b.declaration(BytecodeRootNodeElement.this.asType(), localRootVariable, getRoot);
                getRoot = CodeTreeBuilder.singleString(localRootVariable);
            }

            b.startIf().tree(getRoot).string(".getFrameDescriptor() != frame.getFrameDescriptor()");
            b.end().startBlock();
            emitThrowIllegalArgumentException(b, "Materialized frame belongs to the wrong root node.");
            b.end();

            CodeTree getBytecode = CodeTreeBuilder.createBuilder() //
                            .tree(getRoot) //
                            .string(".getBytecodeNodeImpl()") //
                            .end() //
                            .build();
            if (bytecodeNodeVariable != null) {
                b.declaration(abstractBytecodeNode.asType(), bytecodeNodeVariable, getBytecode);
                getBytecode = CodeTreeBuilder.singleString(bytecodeNodeVariable);
            }

            /**
             * Check that the local is live at the current bci. We can only perform this check when
             * the bci is stored in the frame.
             */
            if (model.enableBlockScoping && model.storeBciInFrame && localIndex != null) {
                b.startAssert().startCall(getBytecode, "validateLocalLivenessInternal");
                b.string("frame");
                b.string("slot");
                b.string(localIndex);
                b.string("stackFrame");
                b.string("bci");
                b.end(2);
            }

        }

        /**
         * We use this method to load constants on the compiled code path.
         *
         * The compiler can often detect and remove redundant box-unbox sequences, but when we load
         * primitives from the constants array that are already boxed, there is no initial "box"
         * operation. By extracting and re-boxing primitive values here, we create a fresh "box"
         * operation with which the compiler can match and eliminate subsequent "unbox" operations.
         */
        private CodeExecutableElement createLoadConstantCompiled() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC, FINAL), type(void.class), "loadConstantCompiled");
            ex.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));
            ex.addParameter(new CodeVariableElement(type(byte[].class), "bc"));
            ex.addParameter(new CodeVariableElement(type(int.class), "bci"));
            ex.addParameter(new CodeVariableElement(type(int.class), "sp"));
            ex.addParameter(new CodeVariableElement(arrayOf(context.getDeclaredType(Object.class)), "consts"));

            CodeTreeBuilder b = ex.createBuilder();
            InstructionImmediate constant = model.loadConstantInstruction.getImmediate(ImmediateKind.CONSTANT);
            b.declaration(context.getDeclaredType(Object.class), "constant", readConstFastPath(readImmediate("bc", "bci", constant)));
            Class<?>[] boxedTypes = new Class<?>[]{Boolean.class, Byte.class, Character.class, Float.class, Integer.class, Long.class, Short.class, Double.class};
            String[] getterMethods = new String[]{"booleanValue", "byteValue", "charValue", "floatValue", "intValue", "longValue", "shortValue", "doubleValue"};
            for (int i = 0; i < boxedTypes.length; i++) {
                b.startIf(i != 0);
                String className = boxedTypes[i].getSimpleName();
                char boundVariable = className.toLowerCase().charAt(0);
                b.string("constant instanceof " + className + " " + boundVariable);
                b.end().startBlock();
                b.statement(setFrameObject("sp", boundVariable + "." + getterMethods[i] + "()"));
                b.statement("return");
                b.end();
            }
            b.statement(setFrameObject("sp", "constant"));

            return ex;
        }

        /**
         * When a node gets re-adopted, the insertion logic validates that the old and new parents
         * both have/don't have a root node. Thus, the bytecode node cannot adopt the operation
         * nodes until the node itself is adopted by the root node. We adopt them after insertion.
         */
        private CodeExecutableElement createAdoptNodesAfterUpdate() {
            CodeExecutableElement ex = GeneratorUtils.override((DeclaredType) abstractBytecodeNode.asType(), "adoptNodesAfterUpdate");
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("insert(this.cachedNodes_)");
            return ex;
        }

        private List<CodeElement<Element>> createBranchProfileMembers() {
            ArrayType branchProfilesType = arrayOf(type(int.class));
            CodeVariableElement branchProfilesField = compFinal(1, new CodeVariableElement(Set.of(PRIVATE, FINAL), branchProfilesType, "branchProfiles_"));

            CodeExecutableElement allocateBranchProfiles = new CodeExecutableElement(Set.of(PRIVATE, STATIC, FINAL), branchProfilesType, "allocateBranchProfiles",
                            new CodeVariableElement(type(int.class), "numProfiles"));
            allocateBranchProfiles.getBuilder() //
                            .lineComment("Encoding: [t1, f1, t2, f2, ..., tn, fn]") //
                            .startReturn().startNewArray(branchProfilesType, CodeTreeBuilder.singleString("numProfiles * 2")).end(2);

            CodeExecutableElement profileBranch = createProfileBranch(branchProfilesType);
            CodeExecutableElement ensureFalseProfile = createEnsureFalseProfile(branchProfilesType);

            return List.of(branchProfilesField, allocateBranchProfiles, profileBranch, ensureFalseProfile);
        }

        /**
         * This code implements the same logic as the CountingConditionProfile.
         */
        private CodeExecutableElement createProfileBranch(TypeMirror branchProfilesType) {
            CodeExecutableElement allocateBranchProfiles = new CodeExecutableElement(Set.of(PRIVATE, STATIC, FINAL), type(boolean.class), "profileBranch",
                            new CodeVariableElement(branchProfilesType, "branchProfiles"),
                            new CodeVariableElement(type(int.class), "profileIndex"),
                            new CodeVariableElement(type(boolean.class), "condition"));

            emitNewBranchProfile(allocateBranchProfiles);

            return allocateBranchProfiles;
        }

        private void emitNewBranchProfile(CodeExecutableElement allocateBranchProfiles) {
            CodeTreeBuilder b = allocateBranchProfiles.createBuilder();
            b.declaration("int", "t", (CodeTree) null);
            b.declaration("int", "f", (CodeTree) null);

            b.startIf().startStaticCall(types.HostCompilerDirectives, "inInterpreterFastPath").end().end().startBlock();

            b.startIf().string("condition").end().startBlock();
            emitNewProfileBranchCase(b, "t", "f", "profileIndex * 2", "profileIndex * 2 + 1");
            b.end().startElseBlock();
            emitNewProfileBranchCase(b, "f", "t", "profileIndex * 2 + 1", "profileIndex * 2");
            b.end();

            b.statement("return condition");

            b.end().startElseBlock(); // inInterpreterFasthPath

            b.startAssign("t").tree(readIntArray("branchProfiles", "profileIndex * 2")).end();
            b.startAssign("f").tree(readIntArray("branchProfiles", "profileIndex * 2 + 1")).end();

            b.startIf().string("condition").end().startBlock();

            b.startIf().string("t == 0").end().startBlock();
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
            b.end();

            b.startIf().string("f == 0").end().startBlock();
            b.returnTrue();
            b.end();

            b.end().startElseBlock(); // condition
            b.startIf().string("f == 0").end().startBlock();
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
            b.end();

            b.startIf().string("t == 0").end().startBlock();
            b.returnFalse();
            b.end();
            b.end(); // condition

            b.startReturn().startStaticCall(types.CompilerDirectives, "injectBranchProbability");
            b.string("(double) t / (double) (t + f)");
            b.string("condition");
            b.end(2);

            b.end();

        }

        private void emitNewProfileBranchCase(CodeTreeBuilder b, String count, String otherCount, String index, String otherIndex) {
            b.startAssign(count).tree(readIntArray("branchProfiles", index)).end();

            b.startIf().string(count).string(" == 0").end().startBlock();
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
            b.end();

            b.startTryBlock();
            b.startAssign(count).startStaticCall(type(Math.class), "addExact").string(count).string("1").end().end();
            b.end().startCatchBlock(type(ArithmeticException.class), "e");
            b.startAssign(otherCount).tree(readIntArray("branchProfiles", otherIndex)).end();
            b.lineComment("shift count but never make it go to 0");
            b.startAssign(otherCount).string("(" + otherCount + " & 0x1) + (" + otherCount + " >> 1)").end();
            b.statement(writeIntArray("branchProfiles", otherIndex, otherCount));
            b.startAssign(count).staticReference(type(Integer.class), "MAX_VALUE").string(" >> 1").end();
            b.end(); // catch block

            b.statement(writeIntArray("branchProfiles", index, count));
        }

        private CodeExecutableElement createEnsureFalseProfile(TypeMirror branchProfilesType) {
            CodeExecutableElement ensureFalseProfile = new CodeExecutableElement(Set.of(PRIVATE, STATIC, FINAL), type(void.class), "ensureFalseProfile",
                            new CodeVariableElement(branchProfilesType, "branchProfiles"),
                            new CodeVariableElement(type(int.class), "profileIndex"));
            CodeTreeBuilder b = ensureFalseProfile.createBuilder();

            b.startIf().tree(readIntArray("branchProfiles", "profileIndex * 2 + 1")).string(" == 0").end().startBlock();
            b.statement(writeIntArray("branchProfiles", "profileIndex * 2 + 1", "1"));
            b.end();

            return ensureFalseProfile;
        }

        private void emitReportLoopCount(CodeTreeBuilder b, CodeTree condition, boolean clear) {
            b.startIf().startStaticCall(types.CompilerDirectives, "hasNextTier").end() //
                            .string(" && ").tree(condition).end().startBlock();
            b.startStatement().startStaticCall(types.LoopNode, "reportLoopCount");
            b.string("this");
            b.string("loopCounter.value");
            b.end(2);
            if (clear) {
                b.statement("loopCounter.value = 0");
            }
            b.end();
        }

        // Generate a helper method that implements the custom instruction. Also emits a call to the
        // helper inside continueAt.
        private void buildCustomInstructionExecute(CodeTreeBuilder continueAtBuilder, InstructionModel instr) {
            // To reduce bytecode in the dispatch loop, extract each implementation into a helper.
            String methodName = instructionMethodName(instr);
            CodeExecutableElement helper = new CodeExecutableElement(Set.of(PRIVATE, FINAL), type(void.class), methodName);
            CodeExecutableElement prev = doInstructionMethods.put(instr, helper);
            if (prev != null) {
                throw new AssertionError("Custom instruction already emitted.");
            }

            helper.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));
            if (model.enableYield) {
                helper.getParameters().add(new CodeVariableElement(types.VirtualFrame, "localFrame"));
            }

            /**
             * These additional parameters mirror the parameters declared in
             * {@link BytecodeDSLNodeGeneratorPlugs#additionalArguments()} (excluding the frames,
             * which are handled specially). They should be kept in sync.
             */
            List<CodeVariableElement> extraParams = createExtraParameters(instr.hasImmediate(ImmediateKind.CONSTANT));

            if (tier.isCached()) {
                helper.getParameters().add(new CodeVariableElement(new ArrayCodeTypeMirror(types.Node), "cachedNodes"));
            }
            helper.getParameters().addAll(extraParams);

            CodeTreeBuilder b = helper.createBuilder();

            // Since an instruction produces at most one value, stackEffect is at most 1.
            int stackEffect = getStackEffect(instr);

            if (customInstructionMayReadBci(instr)) {
                storeBciInFrameIfNecessary(b);
            }

            TypeMirror cachedType = getCachedDataClassType(instr);

            if (tier.isCached()) {
                // If not in the uncached interpreter, we need to retrieve the node for the call.
                if (instr.canUseNodeSingleton()) {
                    b.startDeclaration(cachedType, "node").staticReference(cachedType, "SINGLETON").end();
                } else {
                    CodeTree nodeIndex = readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.NODE_PROFILE));
                    CodeTree readNode = CodeTreeBuilder.createBuilder().tree(readNodeProfile(cachedType, nodeIndex)).build();
                    b.declaration(cachedType, "node", readNode);
                }
            }

            boolean unexpectedValue = hasUnexpectedExecuteValue(instr);
            if (unexpectedValue) {
                b.startTryBlock();
            }

            buildCallExecute(b, instr, null, extraParams);

            // Update the stack.
            if (!instr.signature.isVoid) {
                b.startStatement();
                if (instr.isReturnTypeQuickening()) {
                    startSetFrame(b, instr.signature.returnType);
                } else {
                    startSetFrame(b, type(Object.class));
                }

                b.string("frame");
                if (stackEffect == 1) {
                    b.string("sp");
                } else {
                    b.string("sp - " + (1 - stackEffect));
                }
                b.string("result");
                b.end(); // setFrame
                b.end(); // statement
            }

            if (unexpectedValue) {
                b.end().startCatchBlock(types.UnexpectedResultException, "ex");
                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());

                if (isBoxingOverloadReturnTypeQuickening(instr)) {
                    InstructionModel generic = instr.quickeningBase.findGenericInstruction();
                    if (generic == instr) {
                        throw new AssertionError("Unexpected generic instruction.");
                    }
                    emitQuickening(b, "this", "bc", "bci", null, createInstructionConstant(generic));
                }

                if (!instr.signature.isVoid) {
                    b.startStatement();
                    startSetFrame(b, type(Object.class));
                    b.string("frame");
                    if (stackEffect == 1) {
                        b.string("sp");
                    } else {
                        b.string("sp - " + (1 - stackEffect));
                    }
                    b.string("ex.getResult()");
                    b.end(); // setFrame
                    b.end(); // statement
                }
                b.end(); // catch
            }

            for (int i = stackEffect; i < 0; i++) {
                // When stackEffect is negative, values should be cleared from the top of the stack.
                b.statement(clearFrame("frame", "sp - " + -i));
            }

            // In continueAt, call the helper and adjust sp.
            continueAtBuilder.startStatement().startCall(methodName);
            continueAtBuilder.variables(helper.getParameters());
            continueAtBuilder.end(2);
        }

        private boolean isBoxingOverloadReturnTypeQuickening(InstructionModel instr) {
            if (!instr.isReturnTypeQuickening()) {
                return false;
            }
            SpecializationData specialization = instr.resolveSingleSpecialization();
            if (specialization == null) { // multiple specializations handled
                return false;
            }
            return specialization.getBoxingOverloads().size() > 0;
        }

        private void emitCustomStackEffect(CodeTreeBuilder continueAtBuilder, int stackEffect) {
            if (stackEffect > 0) {
                continueAtBuilder.statement("sp += " + stackEffect);
            } else if (stackEffect < 0) {
                continueAtBuilder.statement("sp -= " + -stackEffect);
            }
        }

        private static int getStackEffect(InstructionModel instr) {
            return (instr.signature.isVoid ? 0 : 1) - instr.signature.dynamicOperandCount;
        }

        private GeneratedTypeMirror getCachedDataClassType(InstructionModel instr) {
            return new GeneratedTypeMirror("", cachedDataClassName(instr));
        }

        private List<CodeVariableElement> createExtraParameters(boolean withConsts) {
            List<CodeVariableElement> extraParams = new ArrayList<>();
            extraParams.addAll(List.of(
                            new CodeVariableElement(type(byte[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(int.class), "sp")));
            if (withConsts) {
                extraParams.add(new CodeVariableElement(new ArrayCodeTypeMirror(type(Object.class)), "consts"));
            }
            return extraParams;
        }

        private void buildCallExecute(CodeTreeBuilder b, InstructionModel instr, String evaluatedArg, List<CodeVariableElement> extraParams) {
            boolean isVoid = instr.signature.isVoid;
            TypeMirror cachedType = getCachedDataClassType(instr);

            b.startStatement();
            if (!isVoid) {
                b.type(instr.signature.returnType);
                b.string(" result = ");
            }
            if (tier.isUncached()) {
                b.staticReference(cachedType, "UNCACHED").startCall(".executeUncached");
            } else {
                b.startCall("node", executeMethodName(instr));
            }

            // If we support yield, the frame forwarded to specializations should be the local frame
            // and not the stack frame.
            b.string(localFrame());

            if (evaluatedArg != null) {
                b.string(evaluatedArg);
            } else if (tier.isUncached()) {
                // The uncached version takes all of its parameters. Other versions compute them.
                List<InstructionImmediate> constants = instr.getImmediates(ImmediateKind.CONSTANT);
                for (int i = 0; i < instr.signature.constantOperandsBeforeCount; i++) {
                    TypeMirror constantOperandType = instr.operation.constantOperands.before().get(i).type();
                    b.startGroup();
                    b.tree(readConstFastPath(readImmediate("bc", "bci", constants.get(i)), constantOperandType));
                    b.end();
                }

                for (int i = 0; i < instr.signature.dynamicOperandCount; i++) {
                    TypeMirror targetType = instr.signature.getGenericType(i);
                    b.startGroup();
                    if (!ElementUtils.isObject(targetType)) {
                        b.cast(targetType);
                    }
                    b.string(uncheckedGetFrameObject("sp - " + (instr.signature.dynamicOperandCount - i)));
                    b.end();
                }

                for (int i = 0; i < instr.signature.constantOperandsAfterCount; i++) {
                    TypeMirror constantOperandType = instr.operation.constantOperands.after().get(i).type();
                    b.startGroup();
                    b.tree(readConstFastPath(readImmediate("bc", "bci", constants.get(i + instr.signature.constantOperandsBeforeCount)), constantOperandType));
                    b.end();
                }
            }

            if (model.enableYield) {
                b.string("frame"); // passed for $stackFrame
            }
            b.string("this");
            b.variables(extraParams);
            b.end(); // call
            b.end(); // statement
        }

        /**
         * When in the uncached interpreter or an interpreter with storeBciInFrame set to true, we
         * need to store the bci in the frame before escaping operations (e.g., returning, yielding,
         * throwing) or potentially-escaping operations (e.g., a custom operation that could invoke
         * another root node).
         */
        private void storeBciInFrameIfNecessary(CodeTreeBuilder b) {
            if (tier.isUncached() || model.storeBciInFrame) {
                b.statement("FRAMES.setInt(" + localFrame() + ", " + BCI_INDEX + ", bci)");
            }
        }

        private static void emitReturnTopOfStack(CodeTreeBuilder b) {
            b.startReturn().string(encodeReturnState("(sp - 1)")).end();
        }

        private void emitBeforeReturnProfiling(CodeTreeBuilder b) {
            if (tier.isCached()) {
                emitReportLoopCount(b, CodeTreeBuilder.singleString("loopCounter.value > 0"), false);
            }
        }

        /**
         * To avoid storing the bci in cases when the operation is simple, we use the heuristic that
         * a node will not escape/read its own bci unless it has a cached value.
         *
         * Note: the caches list includes bind values, so @Bind("$rootNode") is included in the
         * check.
         */
        private boolean customInstructionMayReadBci(InstructionModel instr) {
            for (SpecializationData spec : instr.nodeData.getSpecializations()) {
                if (!spec.getCaches().isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        private String instructionMethodName(InstructionModel instr) {
            return "do" + firstLetterUpperCase(instr.getInternalName());
        }

    }

    final class ContinuationRootNodeImplElement extends CodeTypeElement {

        ContinuationRootNodeImplElement() {
            super(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "ContinuationRootNodeImpl");
            this.setEnclosingElement(BytecodeRootNodeElement.this);
            this.setSuperClass(types.ContinuationRootNode);

            this.add(new CodeVariableElement(Set.of(FINAL), BytecodeRootNodeElement.this.asType(), "root"));
            this.add(new CodeVariableElement(Set.of(FINAL), type(int.class), "sp"));
            this.add(compFinal(new CodeVariableElement(Set.of(VOLATILE), types.BytecodeLocation, "location")));
        }

        void lazyInit() {
            CodeExecutableElement constructor = this.add(GeneratorUtils.createConstructorUsingFields(
                            Set.of(), this,
                            ElementFilter.constructorsIn(((TypeElement) types.RootNode.asElement()).getEnclosedElements()).stream().filter(x -> x.getParameters().size() == 2).findFirst().get()));
            CodeTreeBuilder b = constructor.createBuilder();
            b.statement("super(BytecodeRootNodesImpl.VISIBLE_TOKEN, language, frameDescriptor)");
            b.statement("this.root = root");
            b.statement("this.sp = sp");
            b.statement("this.location = location");

            this.add(createExecute());
            this.add(createGetSourceRootNode());
            this.add(createGetLocation());
            this.add(createFindFrame());
            this.add(createUpdateBytecodeLocation());
            this.add(createUpdateBytecodeLocationWithoutInvalidate());
            this.add(createCreateContinuation());
            this.add(createToString());

            // RootNode overrides.
            this.add(createIsCloningAllowed());
            this.add(createIsCloneUninitializedSupported());
            this.addOptional(createPrepareForCompilation());
            // Should appear last. Uses current method set to determine which methods need to be
            // implemented.
            this.addAll(createRootNodeProxyMethods());
        }

        private CodeExecutableElement createExecute() {
            CodeExecutableElement ex = GeneratorUtils.override(types.RootNode, "execute", new String[]{"frame"});

            CodeTreeBuilder b = ex.createBuilder();

            b.declaration(types.BytecodeLocation, "bytecodeLocation", "location");
            b.startDeclaration(abstractBytecodeNode.asType(), "bytecodeNode");
            b.startGroup().cast(abstractBytecodeNode.asType()).string("bytecodeLocation.getBytecodeNode()").end();
            b.end();

            if (model.usesBoxingElimination()) {
                b.startIf().string("!bytecodeNode.checkStableTagsAssumption()").end().startBlock();
                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                b.end();
            }

            b.statement("Object[] args = frame.getArguments()");
            b.startIf().string("args.length != 2").end().startBlock();
            emitThrowIllegalArgumentException(b, "Expected 2 arguments: (parentFrame, inputValue)");
            b.end();

            b.declaration(types.MaterializedFrame, "parentFrame", "(MaterializedFrame) args[0]");
            b.declaration(type(Object.class), "inputValue", "args[1]");

            b.startIf().string("parentFrame.getFrameDescriptor() != frame.getFrameDescriptor()").end().startBlock();
            emitThrowIllegalArgumentException(b, "Invalid continuation parent frame passed");
            b.end();
            b.startIf().string("parentFrame.getClass() != FRAME_TYPE").end().startBlock();
            emitThrowIllegalArgumentException(b, "Unsupported frame type. Only default frames are supported for continuations.");
            b.end();

            b.lineComment("Copy any existing stack values (from numLocals to sp - 1) to the current frame, which will be used for stack accesses.");
            b.statement(copyFrameTo("parentFrame", "root.maxLocals", "frame", "root.maxLocals", "sp - 1"));
            b.statement(setFrameObject(COROUTINE_FRAME_INDEX, "parentFrame"));
            b.statement(setFrameObject("root.maxLocals + sp - 1", "inputValue"));

            b.startReturn();
            b.startCall("root.continueAt");
            b.string("bytecodeNode");
            b.string("bytecodeLocation.getBytecodeIndex()"); // bci
            b.string("sp + root.maxLocals"); // sp
            b.string("frame");
            b.string("parentFrame");
            b.string("this");
            b.end(2);

            return ex;
        }

        private CodeExecutableElement createGetSourceRootNode() {
            CodeExecutableElement ex = GeneratorUtils.override(types.ContinuationRootNode, "getSourceRootNode");
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn().string("root").end();
            return ex;
        }

        private CodeExecutableElement createGetLocation() {
            CodeExecutableElement ex = GeneratorUtils.override(types.ContinuationRootNode, "getLocation");
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn().string("location").end();
            return ex;
        }

        private CodeExecutableElement createFindFrame() {
            CodeExecutableElement ex = GeneratorUtils.override(types.ContinuationRootNode, "findFrame", new String[]{"frame"});
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn();
            b.cast(types.Frame);
            startGetFrame(b, "frame", type(Object.class), false).string(COROUTINE_FRAME_INDEX).end();
            b.end();
            return ex;
        }

        private CodeExecutableElement createUpdateBytecodeLocation() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "updateBytecodeLocation");
            ex.addParameter(new CodeVariableElement(types.BytecodeLocation, "newLocation"));
            ex.addParameter(new CodeVariableElement(types.BytecodeNode, "oldBytecode"));
            ex.addParameter(new CodeVariableElement(types.BytecodeNode, "newBytecode"));
            ex.addParameter(new CodeVariableElement(context.getDeclaredType(CharSequence.class), "replaceReason"));

            CodeTreeBuilder b = ex.createBuilder();
            b.tree(createNeverPartOfCompilation());
            b.statement("location = newLocation");
            b.startStatement().startCall("reportReplace");
            b.string("oldBytecode");
            b.string("newBytecode");
            b.string("replaceReason");
            b.end(2);
            return ex;
        }

        private CodeExecutableElement createUpdateBytecodeLocationWithoutInvalidate() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "updateBytecodeLocationWithoutInvalidate");
            ex.addParameter(new CodeVariableElement(types.BytecodeLocation, "newLocation"));
            addJavadoc(ex, String.format("""
                            Updates the location without reporting replacement (i.e., without invalidating compiled code).
                            <p>
                            We avoid reporting replacement when an update does not change the bytecode (e.g., a source reparse).
                            Any code path that depends on observing an up-to-date BytecodeNode (e.g., location computations) should
                            not be compiled (it must be guarded by a {@link %s}).
                            """, ElementUtils.getSimpleName(types.CompilerDirectives_TruffleBoundary)));
            CodeTreeBuilder b = ex.createBuilder();
            b.tree(createNeverPartOfCompilation());
            b.statement("location = newLocation");
            return ex;
        }

        private CodeExecutableElement createCreateContinuation() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), types.ContinuationResult, "createContinuation");
            ex.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));
            ex.addParameter(new CodeVariableElement(type(Object.class), "result"));
            CodeTreeBuilder b = ex.createBuilder();

            b.startReturn().startNew(types.ContinuationResult);
            b.string("this");
            b.string("frame.materialize()");
            b.string("result");
            b.end(2);

            return ex;
        }

        private CodeExecutableElement createToString() {
            CodeExecutableElement ex = GeneratorUtils.override(context.getDeclaredType(Object.class), "toString");
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn();
            b.startStaticCall(type(String.class), "format");
            b.doubleQuote("%s(resume_bci=%s)");
            b.string("root");
            b.string("location.getBytecodeIndex()");
            b.end(2);
            return ex;
        }

        private CodeExecutableElement createIsCloningAllowed() {
            CodeExecutableElement ex = GeneratorUtils.override(types.RootNode, "isCloningAllowed");
            CodeTreeBuilder b = ex.createBuilder();
            b.lineComment("Continuations are unique.");
            b.startReturn();
            b.string("false");
            b.end();
            return ex;
        }

        private CodeExecutableElement createIsCloneUninitializedSupported() {
            CodeExecutableElement ex = GeneratorUtils.override(types.RootNode, "isCloneUninitializedSupported");
            CodeTreeBuilder b = ex.createBuilder();
            b.lineComment("Continuations are unique.");
            b.startReturn();
            b.string("false");
            b.end();
            return ex;
        }

        private CodeExecutableElement createPrepareForCompilation() {
            if (!model.enableUncachedInterpreter) {
                return null;
            }

            CodeExecutableElement ex = overrideImplementRootNodeMethod(model, "prepareForCompilation", new String[]{"rootCompilation", "compilationTier", "lastTier"});
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn().startCall("root.prepareForCompilation").string("rootCompilation").string("compilationTier").string("lastTier").end(2);
            return ex;
        }

        private List<CodeExecutableElement> createRootNodeProxyMethods() {
            List<CodeExecutableElement> result = new ArrayList<>();

            List<ExecutableElement> existing = ElementFilter.methodsIn(continuationRootNodeImpl.getEnclosedElements());

            List<ExecutableElement> excludes = List.of(
                            ElementUtils.findMethod(types.RootNode, "copy"),
                            ElementUtils.findMethod(types.RootNode, "cloneUninitialized"));

            outer: for (ExecutableElement rootNodeMethod : ElementUtils.getOverridableMethods((TypeElement) types.RootNode.asElement())) {
                // Exclude methods we have already implemented.
                for (ExecutableElement implemented : existing) {
                    if (ElementUtils.signatureEquals(implemented, rootNodeMethod)) {
                        continue outer;
                    }
                }
                // Exclude methods we do not wish to implement.
                for (ExecutableElement exclude : excludes) {
                    if (ElementUtils.signatureEquals(exclude, rootNodeMethod)) {
                        continue outer;
                    }
                }
                // Only proxy methods overridden by the template class.
                ExecutableElement templateMethod = ElementUtils.findOverride(model.templateType, rootNodeMethod);
                if (templateMethod == null) {
                    continue outer;
                }

                CodeExecutableElement proxyMethod = GeneratorUtils.override(templateMethod);
                CodeTreeBuilder b = proxyMethod.createBuilder();

                boolean isVoid = ElementUtils.isVoid(proxyMethod.getReturnType());
                if (isVoid) {
                    b.startStatement();
                } else {
                    b.startReturn();
                }

                b.startCall("root", rootNodeMethod.getSimpleName().toString());
                for (VariableElement param : rootNodeMethod.getParameters()) {
                    b.variable(param);
                }
                b.end(); // call
                b.end(); // statement / return

                result.add(proxyMethod);
            }

            return result;
        }
    }

    final class ContinuationLocationElement extends CodeTypeElement {

        ContinuationLocationElement() {
            super(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "ContinuationLocation");
            this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(int.class), "constantPoolIndex"));
            this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(int.class), "bci"));
            this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(int.class), "sp"));
            this.add(GeneratorUtils.createConstructorUsingFields(Set.of(), this));
        }

    }

}
