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
package com.oracle.truffle.dsl.processor.bytecode.generator;

import static com.oracle.truffle.dsl.processor.bytecode.generator.ElementHelpers.addField;
import static com.oracle.truffle.dsl.processor.bytecode.generator.ElementHelpers.arrayOf;
import static com.oracle.truffle.dsl.processor.bytecode.generator.ElementHelpers.createInitializedVariable;
import static com.oracle.truffle.dsl.processor.bytecode.generator.ElementHelpers.declaredType;
import static com.oracle.truffle.dsl.processor.bytecode.generator.ElementHelpers.generic;
import static com.oracle.truffle.dsl.processor.bytecode.generator.ElementHelpers.type;
import static com.oracle.truffle.dsl.processor.bytecode.generator.ElementHelpers.wildcard;
import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.addOverride;
import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.createConstructorUsingFields;
import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.createNeverPartOfCompilation;
import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.mergeSuppressWarnings;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.firstLetterUpperCase;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
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
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Function;
import java.util.function.Supplier;
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
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.bytecode.model.BytecodeDSLModel;
import com.oracle.truffle.dsl.processor.bytecode.model.CustomOperationModel;
import com.oracle.truffle.dsl.processor.bytecode.model.DynamicOperandModel;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.ImmediateKind;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionImmediate;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionKind;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel.OperationArgument;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel.OperationKind;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel.OperationArgument.Encoding;
import com.oracle.truffle.dsl.processor.bytecode.model.ShortCircuitInstructionModel;
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
import com.oracle.truffle.dsl.processor.java.model.CodeTypeParameterElement;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.java.model.GeneratedTypeMirror;
import com.oracle.truffle.dsl.processor.model.SpecializationData;

public class BytecodeDSLNodeFactory implements ElementHelpers {

    public static final String USER_LOCALS_START_IDX = "USER_LOCALS_START_IDX";
    public static final String BCI_IDX = "BCI_IDX";
    public static final String COROUTINE_FRAME_IDX = "COROUTINE_FRAME_IDX";
    public static final String EPILOG_EXCEPTION_IDX = "EPILOG_EXCEPTION_IDX";
    public static final String EMPTY_INT_ARRAY = "EMPTY_INT_ARRAY";

    // Bytecode version encoding: [tags][instrumentations][source bit]
    public static final int MAX_TAGS = 32;
    public static final int TAG_OFFSET = 32;
    public static final int MAX_INSTRUMENTATIONS = 31;
    public static final int INSTRUMENTATION_OFFSET = 1;

    private final ProcessorContext context = ProcessorContext.getInstance();
    private final TruffleTypes types = context.getTypes();
    private final BytecodeDSLModel model;

    /**
     * The top-level class that subclasses the node annotated with @GenerateBytecode. All of the
     * definitions that follow are nested inside of this class.
     */
    private final CodeTypeElement bytecodeNodeGen;

    /**
     * We generate several CodeTypeElements to implement a Bytecode DSL interpreter. For each type,
     * a corresponding Factory class generates it and its members.
     * <p>
     * When generating code, some factories need to refer to other generated types. We declare those
     * types here as fields to make them accessible (i.e. the fields below are *not* a complete list
     * of the generated types).
     */

    // The builder class invoked by the language parser to generate the bytecode.
    private final CodeTypeElement builder;
    private final TypeMirror bytecodeBuilderType;
    private final TypeMirror parserType;

    // Root node and ContinuationLocation classes to support yield.
    private final CodeTypeElement continuationRootNodeImpl;
    private final CodeTypeElement continuationLocation;

    // Singleton field for an empty array.
    private final CodeVariableElement emptyObjectArray;

    // Singleton field for accessing arrays and the frame.
    private final CodeVariableElement fastAccess;

    // Implementations of public classes that Truffle interpreters interact with.
    private final CodeTypeElement bytecodeRootNodesImpl = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "BytecodeRootNodesImpl");
    private final CodeTypeElement bytecodeLabelImpl = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "BytecodeLabelImpl");

    // Helper classes that map instructions/operations to constant integral values.
    private final CodeTypeElement instructionsElement = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "Instructions");
    private final CodeTypeElement operationsElement = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "Operations");

    // Helper class that tracks the number of guest-language loop iterations. The count must be
    // wrapped in an object, otherwise the loop unrolling logic of ExplodeLoop.MERGE_EXPLODE will
    // create a new "state" for each count.
    private final CodeTypeElement loopCounter = new CodeTypeElement(Set.of(PRIVATE, STATIC), ElementKind.CLASS, null, "LoopCounter");

    private CodeTypeElement configEncoder;
    private CodeTypeElement abstractBytecodeNode;
    private CodeTypeElement tagNode;
    private CodeTypeElement tagRootNode;
    private CodeTypeElement instructionImpl;

    private final Map<TypeMirror, VariableElement> frameSlotKindConstant = new HashMap<>();

    public BytecodeDSLNodeFactory(BytecodeDSLModel model) {
        this.model = model;
        bytecodeNodeGen = model.generatedType;
        builder = model.builderType;
        bytecodeBuilderType = builder.asType();
        parserType = generic(types.BytecodeParser, bytecodeBuilderType);

        addField(bytecodeNodeGen, Set.of(PRIVATE, STATIC, FINAL), int[].class, EMPTY_INT_ARRAY, "new int[0]");

        if (model.enableYield) {
            continuationRootNodeImpl = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "ContinuationRootNodeImpl");
            continuationLocation = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "ContinuationLocation");
        } else {
            continuationRootNodeImpl = null;
            continuationLocation = null;
        }

        emptyObjectArray = addField(bytecodeNodeGen, Set.of(PRIVATE, STATIC, FINAL), Object[].class, "EMPTY_ARRAY", "new Object[0]");
        fastAccess = addField(bytecodeNodeGen, Set.of(PRIVATE, STATIC, FINAL), types.BytecodeDSLAccess, "ACCESS");
        fastAccess.setInit(createFastAccessFieldInitializer());
    }

    public CodeTypeElement create() {
        // Print a summary of the model in a docstring at the start.
        bytecodeNodeGen.createDocBuilder().startDoc().lines(model.pp()).end();

        // Define constants for accessing the frame.
        bytecodeNodeGen.addAll(createFrameLayoutConstants());

        if (model.usesBoxingElimination()) {
            for (TypeMirror boxingEliminatedType : model.boxingEliminatedTypes) {
                String frameSlotKind = ElementUtils.firstLetterUpperCase(ElementUtils.getSimpleName(boxingEliminatedType));
                CodeVariableElement tag = createFrameSlotKindConstant(frameSlotKind);
                frameSlotKindConstant.put(boxingEliminatedType, tag);
                bytecodeNodeGen.add(tag);
            }
        }

        InstructionImplFactory instructionImplFactory = new InstructionImplFactory();
        this.instructionImpl = instructionImplFactory.type;

        TagNodeFactory tagNodeFactory = null;
        TagRootNodeFactory tagRootNodeFactory = null;
        if (model.enableTagInstrumentation) {
            tagNodeFactory = new TagNodeFactory();
            this.tagNode = bytecodeNodeGen.add(tagNodeFactory.type);

            tagRootNodeFactory = new TagRootNodeFactory();
            this.tagRootNode = bytecodeNodeGen.add(tagRootNodeFactory.type);
        }

        this.abstractBytecodeNode = bytecodeNodeGen.add(new AbstractBytecodeNodeFactory().create());
        if (model.enableTagInstrumentation) {
            tagNodeFactory.create();
            tagRootNodeFactory.create();
        }

        CodeVariableElement bytecodeNode = new CodeVariableElement(Set.of(PRIVATE, VOLATILE), abstractBytecodeNode.asType(), "bytecode");
        bytecodeNodeGen.add(child(bytecodeNode));
        bytecodeNodeGen.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), bytecodeRootNodesImpl.asType(), "nodes"));
        bytecodeNodeGen.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), context.getType(int.class), "numLocals"));
        bytecodeNodeGen.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), context.getType(int.class), "buildIndex"));
        bytecodeNodeGen.add(createBytecodeUpdater());

        // Define the interpreter implementations.
        CodeTypeElement cachedBytecodeNode = bytecodeNodeGen.add(new BytecodeNodeFactory(InterpreterTier.CACHED).create());
        abstractBytecodeNode.getPermittedSubclasses().add(cachedBytecodeNode.asType());

        CodeTypeElement initialBytecodeNode;
        if (model.enableUncachedInterpreter) {
            CodeTypeElement uncachedBytecodeNode = bytecodeNodeGen.add(new BytecodeNodeFactory(InterpreterTier.UNCACHED).create());
            abstractBytecodeNode.getPermittedSubclasses().add(uncachedBytecodeNode.asType());
            initialBytecodeNode = uncachedBytecodeNode;
        } else {
            CodeTypeElement uninitializedBytecodeNode = bytecodeNodeGen.add(new BytecodeNodeFactory(InterpreterTier.UNINITIALIZED).create());
            abstractBytecodeNode.getPermittedSubclasses().add(uninitializedBytecodeNode.asType());
            initialBytecodeNode = uninitializedBytecodeNode;
        }

        // Define the builder class.
        bytecodeNodeGen.add(new BuilderFactory().create());

        instructionImplFactory.create();
        bytecodeNodeGen.add(instructionImplFactory.type);

        configEncoder = bytecodeNodeGen.add(createBytecodeConfigEncoderClass());

        CodeExecutableElement newConfigBuilder = bytecodeNodeGen.add(new CodeExecutableElement(Set.of(PUBLIC, STATIC), types.BytecodeConfig_Builder, "newConfigBuilder"));
        newConfigBuilder.createBuilder().startReturn().startStaticCall(types.BytecodeConfig, "newBuilder").staticReference(configEncoder.asType(), "INSTANCE").end().end();

        // Define implementations for the public classes that Truffle interpreters interact with.
        bytecodeNodeGen.add(new BytecodeRootNodesImplFactory().create());
        bytecodeNodeGen.add(new BytecodeLabelImplFactory().create());

        // Define helper classes containing the constants for instructions and operations.
        bytecodeNodeGen.add(new InstructionConstantsFactory().create());
        bytecodeNodeGen.add(new OperationsConstantsFactory().create());

        bytecodeNodeGen.add(new ExceptionHandlerImplFactory().create());
        bytecodeNodeGen.add(new ExceptionHandlerListFactory().create());
        bytecodeNodeGen.add(new SourceInformationImplFactory().create());
        bytecodeNodeGen.add(new SourceInformationListFactory().create());
        bytecodeNodeGen.add(new SourceInformationTreeImplFactory().create());
        bytecodeNodeGen.add(new LocalVariableImplFactory().create());
        bytecodeNodeGen.add(new LocalVariableListFactory().create());

        // Define the classes that implement continuations (yield).
        if (model.enableYield) {
            bytecodeNodeGen.add(new ContinuationRootNodeImplFactory().create());
            bytecodeNodeGen.add(new ContinuationLocationFactory().create());
        }

        if (model.epilogExceptional != null) {
            bytecodeNodeGen.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "HANDLER_EPILOG_EXCEPTIONAL")).createInitBuilder().string("-1");
        }
        if (model.enableTagInstrumentation) {
            bytecodeNodeGen.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "HANDLER_TAG_EXCEPTIONAL")).createInitBuilder().string("-2");
        }

        // Define the generated node's constructor.
        bytecodeNodeGen.add(createConstructor(initialBytecodeNode));

        // Define the execute method.
        bytecodeNodeGen.add(createExecute());

        // Define a continueAt method.
        // This method delegates to the current tier's continueAt, handling the case where
        // the tier changes.
        bytecodeNodeGen.add(createContinueAt());
        bytecodeNodeGen.add(createTransitionToCached());
        bytecodeNodeGen.add(createUpdateBytecode());
        bytecodeNodeGen.add(createEnsureSources());

        bytecodeNodeGen.add(createIsInstrumentable());
        bytecodeNodeGen.addOptional(createPrepareForInstrumentation());

        bytecodeNodeGen.add(createEncodeTags());
        if (model.enableTagInstrumentation) {
            bytecodeNodeGen.add(createResolveInstrumentableCallNode());
        }

        // Define a loop counter class to track how many back-edges have been taken.
        bytecodeNodeGen.add(createLoopCounter());

        // Define the static method to create a root node.
        bytecodeNodeGen.add(createCreate());

        // Define serialization methods and helper fields.
        if (model.enableSerialization) {
            bytecodeNodeGen.add(createSerialize());
            bytecodeNodeGen.add(createDoSerialize());
            bytecodeNodeGen.add(createDeserialize());
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
            bytecodeNodeGen.add(classToTag);
            CodeExecutableElement classToTagMethod = createMapTagMaskToTagsArray();
            bytecodeNodeGen.add(classToTagMethod);

            CodeExecutableElement initializeTagIndexToClass = bytecodeNodeGen.add(createInitializeTagIndexToClass());
            CodeVariableElement tagToClass = new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), generic(context.getDeclaredType(ClassValue.class), context.getType(Short.class)),
                            "CLASS_TO_TAG_MASK");
            tagToClass.createInitBuilder().startStaticCall(initializeTagIndexToClass).end();
            bytecodeNodeGen.add(tagToClass);
        }

        // Generate a {@link @TracingConfiguration} annotation, if tracing is enabled.
        if (model.enableTracing) {
            bytecodeNodeGen.addAnnotationMirror(createTracingMetadata());
        }

        // Define helper methods for throwing exceptions.
        bytecodeNodeGen.add(createSneakyThrow());
        bytecodeNodeGen.add(createAssertionFailed());

        // Define methods for cloning the root node.
        bytecodeNodeGen.add(createCloneUninitializedSupported());
        bytecodeNodeGen.add(createCloneUninitialized());

        // Define helpers for variadic accesses.
        bytecodeNodeGen.add(createReadVariadic());
        bytecodeNodeGen.add(createMergeVariadic());

        // Define helpers for locals.
        bytecodeNodeGen.add(createGetBytecodeNode());
        bytecodeNodeGen.add(createGetBytecodeNodeImpl());
        bytecodeNodeGen.add(createGetBytecodeRootNode());

        bytecodeNodeGen.add(createGetRootNodes());
        bytecodeNodeGen.add(createGetSourceSection());

        CodeTypeElement cachedNode = bytecodeNodeGen.add(new AbstractCachedNode().create());

        // Define the generated Node classes for custom instructions.
        StaticConstants consts = new StaticConstants();
        for (InstructionModel instr : model.getInstructions()) {
            if (instr.nodeData == null || instr.quickeningBase != null) {
                continue;
            }

            NodeConstants nodeConsts = new NodeConstants();
            BytecodeDSLNodeGeneratorPlugs plugs = new BytecodeDSLNodeGeneratorPlugs(context, this, abstractBytecodeNode.asType(), model, instr);
            FlatNodeGenFactory factory = new FlatNodeGenFactory(context, GeneratorMode.DEFAULT, instr.nodeData, consts, nodeConsts, plugs);

            CodeTypeElement el = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, cachedDataClassName(instr));
            mergeSuppressWarnings(el, "static-method");
            el.setSuperClass(cachedNode.asType());
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
            bytecodeNodeGen.add(el);
        }
        consts.addElementsTo(bytecodeNodeGen);

        if (model.usesBoxingElimination()) {
            for (TypeMirror boxingEliminatedType : model.boxingEliminatedTypes) {
                bytecodeNodeGen.add(createApplyQuickening(boxingEliminatedType));
                bytecodeNodeGen.add(createIsQuickening(boxingEliminatedType));
            }
            bytecodeNodeGen.add(createUndoQuickening());
        }

        if (model.isBytecodeUpdatable()) {
            // we add this last so we do not pick up this field for constructors
            abstractBytecodeNode.add(new CodeVariableElement(Set.of(VOLATILE), arrayOf(type(short.class)), "oldBytecodes"));
        }

        return bytecodeNodeGen;
    }

    private CodeExecutableElement createResolveInstrumentableCallNode() {
        CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.RootNode, "resolveInstrumentableCallNode", 1);
        ex.renameArguments("frame");
        ex.getModifiers().remove(Modifier.ABSTRACT);
        ex.getModifiers().add(Modifier.FINAL);
        CodeTreeBuilder b = ex.createBuilder();

        b.startDeclaration(types.BytecodeLocation, "location").startStaticCall(types.BytecodeLocation, "get").string("frame").end().end();
        b.startIf().string("location == null || !(location.getBytecodeNode() instanceof AbstractBytecodeNode bytecodeNode)").end().startBlock();
        b.startReturn().string("super.resolveInstrumentableCallNode(frame)").end();
        b.end();
        b.statement("return bytecodeNode.findInstrumentableCallNode(location.getBytecodeIndex())");
        return ex;
    }

    private CodeVariableElement createBytecodeUpdater() {
        TypeMirror updaterType = generic(type(AtomicReferenceFieldUpdater.class), bytecodeNodeGen.asType(), abstractBytecodeNode.asType());
        CodeVariableElement bytecodeUpdater = new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), updaterType, "BYTECODE_UPDATER");
        bytecodeUpdater.createInitBuilder().startStaticCall(type(AtomicReferenceFieldUpdater.class), "newUpdater").typeLiteral(bytecodeNodeGen.asType()).typeLiteral(
                        abstractBytecodeNode.asType()).doubleQuote("bytecode").end();
        return bytecodeUpdater;
    }

    private CodeVariableElement createFrameSlotKindConstant(String frameSlotKind) {
        CodeVariableElement tag = new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "TAG_" + frameSlotKind.toUpperCase());
        TypeElement type = ElementUtils.castTypeElement(types.FrameSlotKind);
        int index = 0;
        for (VariableElement var : ElementFilter.fieldsIn(CompilerFactory.getCompiler(type).getAllMembersInDeclarationOrder(context.getEnvironment(), type))) {
            if (var.getKind() != ElementKind.ENUM_CONSTANT) {
                continue;
            }
            if (var.getSimpleName().toString().equals(frameSlotKind)) {
                tag.createInitBuilder().string(index + " /* FrameSlotKind." + frameSlotKind + ".tag */");
                return tag;
            }
            index++;
        }
        throw new AssertionError("Invalid frame slot kind " + frameSlotKind);
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

    static String createApplyQuickeningName(TypeMirror type) {
        return "applyQuickening" + ElementUtils.firstLetterUpperCase(ElementUtils.getSimpleName(type));
    }

    static String createIsQuickeningName(TypeMirror type) {
        return "isQuickening" + ElementUtils.firstLetterUpperCase(ElementUtils.getSimpleName(type));
    }

    public CodeTypeElement getBytecodeNodeGen() {
        return bytecodeNodeGen;
    }

    public CodeTypeElement getAbstractBytecodeNode() {
        return abstractBytecodeNode;
    }

    private Map<TypeMirror, CodeExecutableElement> expectMethods = new HashMap<>();

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
            bytecodeNodeGen.add(expectMethod);
            expectMethods.put(targetType, expectMethod);
        }
        return expectMethod;
    }

    private static String executeMethodName(InstructionModel instruction) {
        return "execute" + instruction.getQualifiedQuickeningName();
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
            result.add(createInitializedVariable(Set.of(PRIVATE, STATIC, FINAL), int.class, BCI_IDX, reserved++ + ""));
        }

        if (model.enableYield) {
            result.add(createInitializedVariable(Set.of(PRIVATE, STATIC, FINAL), int.class, COROUTINE_FRAME_IDX, reserved++ + ""));
        }

        if (model.epilogExceptional != null) {
            result.add(createInitializedVariable(Set.of(PRIVATE, STATIC, FINAL), int.class, EPILOG_EXCEPTION_IDX, reserved++ + ""));
        }

        result.add(createInitializedVariable(Set.of(PRIVATE, STATIC, FINAL), int.class, USER_LOCALS_START_IDX, reserved + ""));

        return result;
    }

    private CodeTree createFastAccessFieldInitializer() {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.startStaticCall(types.BytecodeDSLAccess, "lookup").string("BytecodeRootNodesImpl.VISIBLE_TOKEN").string(Boolean.toString(model.allowUnsafe)).end();
        return b.build();
    }

    private CodeExecutableElement createCloneUninitializedSupported() {
        CodeExecutableElement ex = GeneratorUtils.override(types.RootNode, "isCloneUninitializedSupported");
        ex.createBuilder().returnTrue();
        return ex;
    }

    private CodeExecutableElement createCloneUninitialized() {
        CodeExecutableElement ex = GeneratorUtils.override(types.RootNode, "cloneUninitialized");

        CodeTreeBuilder b = ex.createBuilder();

        b.declaration(bytecodeNodeGen.asType(), "clone", cast(bytecodeNodeGen.asType(), "this.copy()"));
        // The base copy method performs a shallow copy of all fields.
        // Some fields should be manually reinitialized to default values.
        b.statement("clone.bytecode = insert(this.bytecode.cloneUninitialized())");
        emitFence(b);
        b.startReturn().string("clone").end();

        return ex;
    }

    private enum InterpreterTier {
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

    private CodeAnnotationMirror createTracingMetadata() {
        CodeAnnotationMirror mir = new CodeAnnotationMirror(types.BytecodeTracingMetadata);

        mir.setElementValue("decisionsFile", new CodeAnnotationValue(model.decisionsFilePath));

        List<CodeAnnotationValue> instructionNames = model.getInstructions().stream().map(instr -> new CodeAnnotationValue(instr.name)).collect(Collectors.toList());
        // instruction opcodes start at 1. Insert a placeholder element to simplify indexing.
        instructionNames.add(0, new CodeAnnotationValue("NO_INSTRUCTION"));
        mir.setElementValue("instructionNames", new CodeAnnotationValue(instructionNames));

        List<CodeAnnotationValue> specializationNames = model.getInstructions().stream().filter(InstructionModel::hasNodeImmediate).map(instr -> {
            CodeAnnotationMirror instructionSpecializationNames = new CodeAnnotationMirror(types.BytecodeTracingMetadata_SpecializationNames);
            instructionSpecializationNames.setElementValue("instruction", new CodeAnnotationValue(instr.name));

            List<CodeAnnotationValue> specializations = instr.nodeData.getSpecializations().stream().map(spec -> new CodeAnnotationValue(spec.getId())).collect(Collectors.toList());
            instructionSpecializationNames.setElementValue("specializations", new CodeAnnotationValue(specializations));

            return new CodeAnnotationValue(instructionSpecializationNames);
        }).collect(Collectors.toList());
        mir.setElementValue("specializationNames", new CodeAnnotationValue(specializationNames));

        return mir;
    }

    private CodeExecutableElement createConstructor(CodeTypeElement initialBytecodeNode) {
        CodeExecutableElement ctor = new CodeExecutableElement(Set.of(PRIVATE), null, bytecodeNodeGen.getSimpleName().toString());
        ctor.addParameter(new CodeVariableElement(types.TruffleLanguage, "language"));
        ctor.addParameter(new CodeVariableElement(types.FrameDescriptor_Builder, "builder"));
        ctor.addParameter(new CodeVariableElement(bytecodeRootNodesImpl.asType(), "nodes"));
        ctor.addParameter(new CodeVariableElement(type(int.class), "numLocals"));
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
        b.statement("this.numLocals = numLocals");
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
        CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.RootNode, "execute");
        ex.renameArguments("frame");

        CodeTreeBuilder b = ex.createBuilder();

        b.startReturn().startCall("continueAt");
        b.string("bytecode");
        b.string("numLocals << 16");
        b.string("frame");
        if (model.enableYield) {
            b.string("frame");
            b.string("null");
        }

        b.end(2);

        return ex;
    }

    private CodeExecutableElement createGetSourceSection() {
        CodeExecutableElement ex = GeneratorUtils.override(types.Node, "getSourceSection");
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn().string("bytecode.getSourceSection()").end();
        return ex;
    }

    private CodeExecutableElement createContinueAt() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(Object.class), "continueAt");
        ex.addParameter(new CodeVariableElement(abstractBytecodeNode.asType(), "bc"));
        ex.addParameter(new CodeVariableElement(context.getType(int.class), "startState"));
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

        b.statement("int state = startState");

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

        b.startIf().string("(state & 0xFFFF) == 0xFFFF").end().startBlock();
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

        String returnValue = getFrameObject("(state >> 16) & 0xFFFF");
        b.startReturn().string(returnValue).end();

        mergeSuppressWarnings(ex, "all");
        return ex;
    }

    private CodeExecutableElement createSneakyThrow() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), context.getType(RuntimeException.class), "sneakyThrow");

        TypeMirror throwable = context.getType(Throwable.class);
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
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), context.getType(AssertionError.class), "assertionFailed");
        CodeVariableElement param = new CodeVariableElement(context.getType(String.class), "message");
        ex.addParameter(param);

        CodeTreeBuilder b = ex.createBuilder();
        emitThrow(b, AssertionError.class, "message");

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
        ex.addParameter(new CodeVariableElement(types.BytecodeConfig, "config"));
        ex.addParameter(new CodeVariableElement(parserType, "parser"));

        addJavadoc(ex, String.format("""
                        Creates one or more bytecode nodes. This is the entrypoint for creating new {@link %s} instances.

                        @param config indicates whether to parse metadata (e.g., source information).
                        @param parser the parser that invokes a series of builder instructions to generate bytecode.
                        """, model.getName()));

        CodeTreeBuilder b = ex.getBuilder();

        b.declaration("BytecodeRootNodesImpl", "nodes", "new BytecodeRootNodesImpl(parser, config)");
        b.startAssign("Builder builder").startNew(builder.getSimpleName().toString());
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
        TypeMirror classValueType = generic(classValue, context.getType(Short.class));

        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), classValueType,
                        "initializeTagMaskToClass");
        CodeTreeBuilder b = method.createBuilder();

        b.startStatement();
        b.string("return new ClassValue<>()").startBlock();
        b.string("protected Short computeValue(Class<?> type) ").startBlock();

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
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PUBLIC, STATIC), context.getType(void.class), "serialize");
        method.addParameter(new CodeVariableElement(context.getType(DataOutput.class), "buffer"));
        method.addParameter(new CodeVariableElement(types.BytecodeSerializer, "callback"));
        method.addParameter(new CodeVariableElement(parserType, "parser"));
        method.addThrownType(context.getType(IOException.class));

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
        init.startNew(bytecodeBuilderType);
        init.startGroup();
        init.startNew(bytecodeRootNodesImpl.asType());
        init.string("parser");
        init.staticReference(types.BytecodeConfig, "COMPLETE");
        init.end(2);
        init.staticReference(types.BytecodeConfig, "COMPLETE");
        init.end();

        CodeTreeBuilder b = method.createBuilder();

        b.declaration(bytecodeBuilderType, "builder", init.build());

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
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), context.getType(void.class), "doSerialize");
        method.addParameter(new CodeVariableElement(context.getType(DataOutput.class), "buffer"));
        method.addParameter(new CodeVariableElement(types.BytecodeSerializer, "callback"));
        method.addParameter(new CodeVariableElement(bytecodeBuilderType, "builder"));
        method.addParameter(new CodeVariableElement(generic(List.class, model.getTemplateType().asType()), "existingNodes"));
        method.addThrownType(context.getType(IOException.class));

        CodeTreeBuilder b = method.createBuilder();

        b.startTryBlock();

        b.startStatement().startCall("builder", "serialize");
        b.string("buffer");
        b.string("callback");
        b.string("existingNodes");
        b.end().end();

        b.end().startCatchBlock(context.getType(IOError.class), "e");
        b.startThrow().cast(context.getType(IOException.class), "e.getCause()").end();
        b.end();

        return withTruffleBoundary(method);
    }

    private CodeExecutableElement createDeserialize() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PUBLIC, STATIC),
                        generic(types.BytecodeRootNodes, model.getTemplateType().asType()), "deserialize");

        method.addParameter(new CodeVariableElement(types.TruffleLanguage, "language"));
        method.addParameter(new CodeVariableElement(types.BytecodeConfig, "config"));
        method.addParameter(new CodeVariableElement(generic(Supplier.class, DataInput.class), "input"));
        method.addParameter(new CodeVariableElement(types.BytecodeDeserializer, "callback"));
        method.addThrownType(context.getType(IOException.class));

        addJavadoc(method,
                        """
                                        Deserializes a byte sequence to bytecode nodes. The bytes must have been produced by a previous call to {@link #serialize}.").newLine()

                                        @param language the language instance.
                                        @param config indicates whether to deserialize metadata (e.g., source information), if available.
                                        @param input A function that supplies the bytes to deserialize. This supplier must produce a new {@link DataInput} each time, since the bytes may be processed multiple times for reparsing.
                                        @param callback The language-specific deserializer for constants in the bytecode. This callback must perform the inverse of the callback that was used to {@link #serialize} the nodes to bytes.
                                        """);

        CodeTreeBuilder b = method.createBuilder();

        b.startTryBlock();

        b.statement("return create(config, (b) -> b.deserialize(language, input, callback, null))");
        b.end().startCatchBlock(type(IOError.class), "e");
        b.startThrow().cast(type(IOException.class), "e.getCause()").end();
        b.end();

        return withTruffleBoundary(method);
    }

    private CodeExecutableElement createReadVariadic() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), context.getType(Object[].class), "readVariadic");

        ex.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));
        ex.addParameter(new CodeVariableElement(context.getType(int.class), "sp"));
        ex.addParameter(new CodeVariableElement(context.getType(int.class), "variadicCount"));

        ex.addAnnotationMirror(createExplodeLoopAnnotation(null));

        CodeTreeBuilder b = ex.createBuilder();

        b.statement("Object[] result = new Object[variadicCount]");
        b.startFor().string("int i = 0; i < variadicCount; i++").end().startBlock();
        b.statement("int index = sp - variadicCount + i");
        b.statement("result[i] = " + getFrameObject("index"));
        b.statement(clearFrame("frame", "index"));
        b.end();

        b.statement("return result");

        return ex;
    }

    private CodeExecutableElement createMergeVariadic() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), context.getType(Object[].class), "mergeVariadic");

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

    static Object[] merge(Object[] array0, Object[] array1) {
        assert array0.length >= 8;
        assert array1.length > 0;

        Object[] newArray = new Object[array0.length + array1.length];
        System.arraycopy(array0, 0, newArray, 0, array0.length);
        System.arraycopy(array1, 0, newArray, array0.length, array0.length);
        return newArray;
    }

    private void serializationWrapException(CodeTreeBuilder b, Runnable r) {
        b.startTryBlock();
        r.run();
        b.end().startCatchBlock(context.getType(IOException.class), "ex");
        b.startThrow().startNew(context.getType(IOError.class)).string("ex").end(2);
        b.end();
    }

    private CodeExecutableElement createGetBytecodeNode() {
        CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeRootNode, "getBytecodeNode");

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
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), bytecodeNodeGen.asType(), "getBytecodeRootNodeImpl");
        ex.addParameter(new CodeVariableElement(type(int.class), "index"));
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn().cast(bytecodeNodeGen.asType()).string("this.nodes.getNode(index)").end();
        return ex;
    }

    private CodeExecutableElement createGetRootNodes() {
        CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeRootNode, "getRootNodes");
        ex.setReturnType(generic(types.BytecodeRootNodes, model.templateType.asType()));
        ex.getModifiers().remove(Modifier.DEFAULT);
        ex.getModifiers().add(Modifier.FINAL);

        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn().string("this.nodes").end();

        return ex;
    }

    private CodeExecutableElement createTransitionToCached() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "transitionToCached");
        CodeTreeBuilder b = ex.createBuilder();
        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
        b.declaration(abstractBytecodeNode.asType(), "oldBytecode");
        b.declaration(abstractBytecodeNode.asType(), "newBytecode");
        b.startDoBlock();
        b.statement("oldBytecode = this.bytecode");
        b.statement("newBytecode = insert(oldBytecode.toCached())");
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
            if (model.enableYield) {
                b.string("continuationLocations");
            }
            b.end(2);
            b.end();

            if (model.enableYield) {
                // We need to patch the BytecodeNodes for continuations.
                b.startElseIf().string("!continuationLocations.isEmpty()").end().startBlock();
                b.startStatement().startCall("oldBytecode.updateContinuationsWithoutInvalidate");
                b.string("newBytecode");
                b.string("continuationLocations");
                b.end(2);
                b.end();
            }
        }
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

        CodeExecutableElement encodeTag = GeneratorUtils.overrideImplement(types.BytecodeConfigEncoder, "encodeTag");
        encodeTag.renameArguments("c");
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
        CodeExecutableElement encodeInstrumentation = GeneratorUtils.overrideImplement(types.BytecodeConfigEncoder, "encodeInstrumentation");
        encodeInstrumentation.renameArguments("c");
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

    private CodeExecutableElement createEnsureSources() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), abstractBytecodeNode.asType(), "ensureSources");
        CodeTreeBuilder b = ex.createBuilder();
        b.statement("nodes.ensureSources()");
        b.declaration(abstractBytecodeNode.asType(), "newBytecode", "this.bytecode");
        b.statement("assert newBytecode.sourceInfo != null");
        b.startReturn().string("newBytecode").end();
        return ex;
    }

    private CodeExecutableElement createIsInstrumentable() {
        CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.RootNode, "isInstrumentable");
        CodeTreeBuilder b = ex.createBuilder();
        if (model.enableTagInstrumentation) {
            b.statement("return true");
        } else {
            b.statement("return false");
        }
        return ex;
    }

    private CodeExecutableElement createPrepareForInstrumentation() {
        if (!model.enableTagInstrumentation) {
            return null;
        }
        CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.RootNode, "prepareForInstrumentation");
        ex.renameArguments("materializedTags");
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

    private static boolean needsCachedInitialization(InstructionImmediate immediate) {
        return switch (immediate.kind()) {
            case BRANCH_PROFILE, NODE_PROFILE -> true;
            default -> false;
        };
    }

    void emitQuickening(CodeTreeBuilder b, String node, String bc, String bci, CodeTree oldInstruction, CodeTree newInstruction) {
        if (model.specializationDebugListener && oldInstruction == null) {
            b.startBlock();
            b.startDeclaration(instructionImpl.asType(), "oldInstruction");
            String old = bc + "[" + bci + "]";
            emitParseInstruction(b, node, bci, CodeTreeBuilder.singleString(old));
            b.end();
        }

        b.startStatement().startCall("ACCESS.shortArrayWrite");
        b.string(bc).string(bci).tree(newInstruction);
        b.end().end();

        if (model.specializationDebugListener) {
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
        if (model.specializationDebugListener && oldInstruction == null) {
            b.startBlock();
            b.startDeclaration(instructionImpl.asType(), "oldInstruction");
            String old = bc + "[" + bci + "]";
            emitParseInstruction(b, node, bci, CodeTreeBuilder.singleString(old));
            b.end();
        }

        b.startStatement().startCall("ACCESS.shortArrayWrite");
        b.string(bc).string(bci).tree(newInstruction);
        b.end().end();

        if (model.specializationDebugListener) {
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

        if (model.specializationDebugListener && oldInstruction == null) {
            b.startBlock();
            b.startDeclaration(instructionImpl.asType(), "oldInstruction");
            String old = bc + "[" + operandBci + "]";
            emitParseInstruction(b, node, operandBci, CodeTreeBuilder.singleString(old));
            b.end();
        }

        b.startStatement().startCall("ACCESS.shortArrayWrite");
        b.string(bc).string(operandBci).string(newInstruction);
        b.end().end();

        if (model.specializationDebugListener) {
            b.startStatement();
            b.startCall(node, "getRoot().onQuickenOperand");
            String base = baseInstruction == null ? bc + "[" + baseBci + "]" : baseInstruction;
            emitParseInstruction(b, node, baseBci, CodeTreeBuilder.singleString(base));
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

    void emitOnSpecialize(CodeTreeBuilder b, String node, String bci, String instruction, String specializationName) {
        if (model.specializationDebugListener) {
            b.startStatement().startCall(node, "getRoot().onSpecialize");
            emitParseInstruction(b, node, bci, CodeTreeBuilder.singleString(instruction));
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

    final class SerializationStateElements implements ElementHelpers {

        final CodeTypeElement serializationState = new CodeTypeElement(Set.of(PRIVATE, STATIC), ElementKind.CLASS, null, "SerializationState");

        final CodeVariableElement codeCreateLabel = addField(serializationState, Set.of(PRIVATE, STATIC, FINAL), short.class, "CODE_$CREATE_LABEL", "-2");
        final CodeVariableElement codeCreateLocal = addField(serializationState, Set.of(PRIVATE, STATIC, FINAL), short.class, "CODE_$CREATE_LOCAL", "-3");
        final CodeVariableElement codeCreateObject = addField(serializationState, Set.of(PRIVATE, STATIC, FINAL), short.class, "CODE_$CREATE_OBJECT", "-4");
        final CodeVariableElement codeCreateFinallyParser = addField(serializationState, Set.of(PRIVATE, STATIC, FINAL), short.class, "CODE_$CREATE_FINALLY_PARSER", "-5");
        final CodeVariableElement codeEndFinallyParser = addField(serializationState, Set.of(PRIVATE, STATIC, FINAL), short.class, "CODE_$END_FINALLY_PARSER", "-6");
        final CodeVariableElement codeEndSerialize = addField(serializationState, Set.of(PRIVATE, STATIC, FINAL), short.class, "CODE_$END", "-7");

        final CodeVariableElement buffer = addField(serializationState, Set.of(PRIVATE, FINAL), DataOutput.class, "buffer");
        final CodeVariableElement callback = addField(serializationState, Set.of(PRIVATE, FINAL), types.BytecodeSerializer, "callback");
        final CodeVariableElement outer = addField(serializationState, Set.of(PRIVATE, FINAL), serializationState.asType(), "outer");
        final CodeVariableElement depth = addField(serializationState, Set.of(PRIVATE, FINAL), type(int.class), "depth");
        final CodeVariableElement objects = addField(serializationState, Set.of(PRIVATE, FINAL),
                        generic(HashMap.class, Object.class, Short.class), "objects");
        final CodeVariableElement builtNodes = addField(serializationState, Set.of(PRIVATE, FINAL), generic(ArrayList.class, model.getTemplateType().asType()), "builtNodes");
        final CodeVariableElement rootStack = addField(serializationState, Set.of(PRIVATE, FINAL), generic(ArrayDeque.class, bytecodeNodeGen.asType()), "rootStack");

        final CodeExecutableElement constructor = serializationState.add(new CodeExecutableElement(Set.of(PRIVATE), null, serializationState.getSimpleName().toString()));
        final CodeExecutableElement pushConstructor = serializationState.add(new CodeExecutableElement(Set.of(PRIVATE), null, serializationState.getSimpleName().toString()));

        final CodeVariableElement language = addField(serializationState, Set.of(PRIVATE), types.TruffleLanguage, "language");
        final CodeVariableElement labelCount = addField(serializationState, Set.of(PRIVATE), int.class, "labelCount");
        final CodeVariableElement localCount = addField(serializationState, Set.of(PRIVATE), int.class, "localCount");
        final CodeVariableElement rootCount = addField(serializationState, Set.of(PRIVATE), short.class, "rootCount");
        final CodeVariableElement finallyParserCount = addField(serializationState, Set.of(PRIVATE), int.class, "finallyParserCount");

        final CodeVariableElement[] codeBegin;
        final CodeVariableElement[] codeEnd;

        SerializationStateElements() {
            serializationState.getImplements().add(types.BytecodeSerializer_SerializerContext);

            objects.createInitBuilder().startNew("HashMap<>").end();
            builtNodes.createInitBuilder().startNew("ArrayList<>").end();
            rootStack.createInitBuilder().startNew("ArrayDeque<>").end();

            codeBegin = new CodeVariableElement[model.getOperations().size() + 1];
            codeEnd = new CodeVariableElement[model.getOperations().size() + 1];

            // Only allocate serialization codes for non-internal operations.
            for (OperationModel o : model.getUserOperations()) {
                if (o.hasChildren()) {
                    codeBegin[o.id] = addField(serializationState, Set.of(PRIVATE, STATIC, FINAL), short.class,
                                    "CODE_BEGIN_" + ElementUtils.createConstantName(o.name), String.valueOf(o.id) + " << 1");
                    codeEnd[o.id] = addField(serializationState, Set.of(PRIVATE, STATIC, FINAL), short.class,
                                    "CODE_END_" + ElementUtils.createConstantName(o.name), "(" + String.valueOf(o.id) + " << 1) | 0b1");
                } else {
                    codeBegin[o.id] = addField(serializationState, Set.of(PRIVATE, STATIC, FINAL), short.class,
                                    "CODE_EMIT_" + ElementUtils.createConstantName(o.name), String.valueOf(o.id) + " << 1");
                }
            }

            createConstructor();
            createPushConstructor();

            serializationState.add(createSerializeObject());
            serializationState.add(createWriteBytecodeNode());
            serializationState.add(createNextBuildIndex());
        }

        private CodeExecutableElement createConstructor() {
            constructor.addParameter(new CodeVariableElement(buffer.getType(), buffer.getName()));
            constructor.addParameter(new CodeVariableElement(callback.getType(), callback.getName()));

            CodeTreeBuilder b = constructor.createBuilder();

            b.startAssign("this", buffer).variable(buffer).end();
            b.startAssign("this", callback).variable(callback).end();
            b.startAssign("this", outer).string("null").end();
            b.startAssign("this", depth).string("0").end();

            return constructor;
        }

        private CodeExecutableElement createPushConstructor() {
            pushConstructor.addParameter(new CodeVariableElement(buffer.getType(), buffer.getName()));
            pushConstructor.addParameter(new CodeVariableElement(serializationState.asType(), outer.getName()));

            CodeTreeBuilder b = pushConstructor.createBuilder();

            b.startAssign("this", buffer).variable(buffer).end();
            b.startAssign("this", callback).field(outer.getName(), callback).end();
            b.startAssign("this", outer).variable(outer).end();
            b.startAssign("this", depth).startGroup().field(outer.getName(), depth).string(" + 1").end(2);

            return pushConstructor;
        }

        private CodeExecutableElement createWriteBytecodeNode() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeSerializer_SerializerContext, "writeBytecodeNode");
            mergeSuppressWarnings(ex, "hiding");
            ex.renameArguments("buffer", "node");
            CodeTreeBuilder b = ex.createBuilder();
            b.startStatement();
            b.string("buffer.writeInt((");
            b.cast(bytecodeNodeGen.asType()).string("node).buildIndex)");
            b.end();

            return ex;
        }

        private CodeExecutableElement createSerializeObject() {
            CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE), type(short.class), "serializeObject");
            method.addParameter(new CodeVariableElement(type(Object.class), "object"));
            method.addThrownType(type(IOException.class));
            CodeTreeBuilder b = method.createBuilder();

            String argumentName = "object";
            String index = "index";

            b.startAssign("Short " + index).startCall("objects.get").string(argumentName).end(2);
            b.startIf().string(index + " == null").end().startBlock();
            b.startAssign(index).startCall("(short) objects.size").end(2);
            b.startStatement().startCall("objects.put").string(argumentName).string(index).end(2);

            b.startStatement();
            b.string(buffer.getName(), ".").startCall("writeShort").string(codeCreateObject.getName()).end();
            b.end();
            b.statement("callback.serialize(this, buffer, object)");
            b.end();
            b.statement("return ", index);
            return method;
        }

        private CodeExecutableElement createNextBuildIndex() {
            CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE), type(int.class), "nextBuildIndex");
            CodeTreeBuilder b = method.createBuilder();

            b.startIf().variable(rootCount).string(" == ").staticReference(declaredType(Short.class), "MAX_VALUE").end().startBlock();
            emitThrowAssertionError(b, "\"Serialization root count exceeds the maximum short value.\"");
            b.end().startElseIf().variable(depth).string(" > ").staticReference(declaredType(Short.class), "MAX_VALUE").end().startBlock();
            emitThrowAssertionError(b, "\"Serialization depth exceeds the maximum short value.\"");
            b.end();
            b.startReturn().string("(").variable(depth).string(" << 16) | ").variable(rootCount).string("++").end();

            return method;
        }

        public void writeShort(CodeTreeBuilder b, CodeVariableElement label) {
            writeShort(b, b.create().staticReference(label).build());
        }

        public void writeShort(CodeTreeBuilder b, String value) {
            writeShort(b, CodeTreeBuilder.singleString(value));
        }

        public void writeShort(CodeTreeBuilder b, CodeTree value) {
            b.startStatement();
            b.string("serialization.", buffer.getName(), ".").startCall("writeShort");
            b.tree(value).end();
            b.end();
        }

        public void writeInt(CodeTreeBuilder b, String value) {
            writeInt(b, CodeTreeBuilder.singleString(value));
        }

        public void writeInt(CodeTreeBuilder b, CodeTree value) {
            b.startStatement();
            b.string("serialization.", buffer.getName(), ".").startCall("writeInt");
            b.tree(value).end();
            b.end();
        }

        public void writeBytes(CodeTreeBuilder b, String value) {
            writeBytes(b, CodeTreeBuilder.singleString(value));
        }

        public void writeBytes(CodeTreeBuilder b, CodeTree value) {
            b.startStatement();
            b.string("serialization.", buffer.getName(), ".").startCall("write");
            b.tree(value).end();
            b.end();
        }

    }

    class BuilderFactory {
        public static final String UNINIT = "UNINITIALIZED";
        CodeVariableElement uninitialized = new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), context.getType(short.class), UNINIT);
        CodeTypeElement savedState = new CodeTypeElement(Set.of(PRIVATE, STATIC), ElementKind.CLASS, null, "SavedState");
        CodeTypeElement operationStackEntry = new CodeTypeElement(Set.of(PRIVATE, STATIC), ElementKind.CLASS, null, "OperationStackEntry");
        CodeTypeElement constantPool = new CodeTypeElement(Set.of(PRIVATE, STATIC), ElementKind.CLASS, null, "ConstantPool");
        CodeTypeElement unresolvedBranchImmediate = new CodeTypeElement(Set.of(PRIVATE, STATIC), ElementKind.CLASS, null, "UnresolvedBranchTarget");

        private final CodeTypeElement bytecodeLocalImpl = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "BytecodeLocalImpl");

        TypeMirror unresolvedLabelsType = generic(HashMap.class, types.BytecodeLabel, generic(context.getDeclaredType(ArrayList.class), context.getDeclaredType(Integer.class)));

        Map<Integer, CodeExecutableElement> doEmitInstructionMethods = new TreeMap<>();

        final Map<OperationModel, CodeTypeElement> dataClasses = new HashMap<>();

        CodeTypeElement scopeDataType;

        List<CodeVariableElement> builderState = new ArrayList<>();
        {
            builderState.addAll(List.of(
                            new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "operationSequenceNumber"),
                            new CodeVariableElement(Set.of(PRIVATE), new ArrayCodeTypeMirror(operationStackEntry.asType()), "operationStack"),
                            new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "operationSp"),
                            new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "rootOperationSp"),
                            new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "numLocals"),
                            new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "numLabels"),
                            new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "numNodes"),
                            new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "numConditionalBranches"),
                            new CodeVariableElement(Set.of(PRIVATE), context.getType(short[].class), "bc"),
                            new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "bci"),
                            new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "currentStackHeight"),
                            new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "maxStackHeight"),
                            new CodeVariableElement(Set.of(PRIVATE), context.getType(int[].class), "sourceInfo"),
                            new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "sourceInfoIndex"),
                            new CodeVariableElement(Set.of(PRIVATE), context.getType(int[].class), "exHandlers"),
                            new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "exHandlerCount"),
                            new CodeVariableElement(Set.of(PRIVATE), arrayOf(type(int.class)), "locals"),
                            new CodeVariableElement(Set.of(PRIVATE), unresolvedLabelsType, "unresolvedLabels"),
                            new CodeVariableElement(Set.of(PRIVATE), constantPool.asType(), "constantPool")));

            CodeVariableElement reachable = new CodeVariableElement(Set.of(PRIVATE), context.getType(boolean.class), "reachable");
            reachable.createInitBuilder().string("true");
            builderState.add(reachable);

            if (model.enableTracing) {
                builderState.add(new CodeVariableElement(Set.of(PRIVATE), context.getType(boolean[].class), "basicBlockBoundary"));
            }

            if (model.enableYield) {
                builderState.add(
                                /**
                                 * Invariant: Continuation locations are sorted by bci, which means
                                 * we can iterate over the bytecodes and continuation locations in
                                 * lockstep (i.e., the i-th yield instruction uses the i-th
                                 * continuation location).
                                 */
                                new CodeVariableElement(Set.of(PRIVATE), generic(ArrayList.class, continuationLocation.asType()), "continuationLocations"));
            }

            if (model.enableLocalScoping) {
                builderState.add(new CodeVariableElement(Set.of(PRIVATE), type(int.class), "maxLocals"));
            }

            if (model.enableTagInstrumentation) {
                builderState.add(new CodeVariableElement(Set.of(PRIVATE), generic(type(List.class), tagNode.asType()), "tagRoots"));
                builderState.add(new CodeVariableElement(Set.of(PRIVATE), generic(type(List.class), tagNode.asType()), "tagNodes"));
            }

            // must be last
            builderState.add(new CodeVariableElement(Set.of(PRIVATE), savedState.asType(), "savedState"));
        }

        class SavedStateFactory {
            private CodeTypeElement create() {
                savedState.addAll(builderState);
                savedState.add(createConstructorUsingFields(Set.of(), savedState, null));

                return savedState;
            }
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

        class OperationDataClassesFactory {

            private Collection<CodeTypeElement> create() {
                scopeDataType = new CodeTypeElement(Set.of(PRIVATE, STATIC, ABSTRACT), ElementKind.CLASS, null, "ScopeData");
                scopeDataType.add(new CodeVariableElement(Set.of(), type(int.class), "frameOffset"));
                scopeDataType.add(new CodeVariableElement(Set.of(), type(int[].class), "locals"));
                scopeDataType.add(new CodeVariableElement(Set.of(), type(int.class), "localsCount"));
                scopeDataType.add(new CodeVariableElement(Set.of(), type(boolean.class), "valid")).createInitBuilder().string("true");
                List<CodeTypeElement> result = new ArrayList<>();
                result.add(scopeDataType);

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
                                        field(types.TruffleLanguage, "language").asFinal(),
                                        field(type(int.class), "index").asFinal(),
                                        field(type(boolean.class), "producedValue").withInitializer("false"),
                                        field(type(int.class), "childBci").withInitializer(UNINIT),
                                        field(type(boolean.class), "reachable").withInitializer("true")));
                        if (model.prolog != null && model.prolog.operation.operationEndArguments.length != 0) {
                            fields.add(field(type(int.class), "prologBci").withInitializer(UNINIT));
                        }
                        if (model.enableLocalScoping) {
                            superType = scopeDataType.asType();
                        }
                        break;
                    case BLOCK:
                        name = "BlockData";
                        fields = List.of(//
                                        field(context.getType(int.class), "startStackHeight").asFinal(),
                                        field(context.getType(boolean.class), "producedValue").withInitializer("false"),
                                        field(context.getType(int.class), "childBci").withInitializer(UNINIT));
                        if (model.enableLocalScoping) {
                            superType = scopeDataType.asType();
                        }
                        break;
                    case TAG:
                        name = "TagOperationData";
                        fields = List.of(//
                                        field(context.getType(int.class), "nodeId").asFinal(),
                                        field(context.getType(boolean.class), "operationReachable").asFinal(),
                                        field(context.getType(int.class), "startStackHeight").asFinal(),
                                        field(tagNode.asType(), "node").asFinal(),
                                        field(context.getType(boolean.class), "producedValue").withInitializer("false"),
                                        field(context.getType(int.class), "childBci").withInitializer(UNINIT),
                                        field(generic(type(List.class), tagNode.asType()), "children").withInitializer("null"));

                        break;
                    case SOURCE_SECTION:
                        name = "SourceSectionData";
                        fields = List.of(//
                                        field(context.getType(int.class), "sourceIndex").asFinal(),
                                        field(context.getType(int.class), "beginBci").asFinal(),
                                        field(context.getType(int.class), "start").asFinal(),
                                        field(context.getType(int.class), "length").asFinal(),
                                        field(context.getType(boolean.class), "producedValue").withInitializer("false"),
                                        field(context.getType(int.class), "childBci").withInitializer(UNINIT));
                        break;
                    case SOURCE:
                        name = "SourceData";
                        fields = List.of(//
                                        field(context.getType(int.class), "sourceIndex").asFinal(),
                                        field(context.getType(boolean.class), "producedValue").withInitializer("false"),
                                        field(context.getType(int.class), "childBci").withInitializer(UNINIT));
                        break;
                    case RETURN:
                        name = "ReturnOperationData";
                        fields = List.of(//
                                        field(context.getType(boolean.class), "producedValue").withInitializer("false"),
                                        field(context.getType(int.class), "childBci").withInitializer(UNINIT));
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
                                        field(context.getType(boolean.class), "thenReachable"),
                                        field(context.getType(int.class), "falseBranchFixupBci").withInitializer(UNINIT));
                        break;
                    case IF_THEN_ELSE:
                        name = "IfThenElseData";
                        fields = List.of(//
                                        field(context.getType(boolean.class), "thenReachable"),
                                        field(context.getType(boolean.class), "elseReachable"),
                                        field(context.getType(int.class), "falseBranchFixupBci").withInitializer(UNINIT),
                                        field(context.getType(int.class), "endBranchFixupBci").withInitializer(UNINIT));
                        break;
                    case CONDITIONAL:
                        name = "ConditionalData";
                        if (model.usesBoxingElimination()) {
                            fields = List.of(//
                                            field(context.getType(boolean.class), "thenReachable"),
                                            field(context.getType(boolean.class), "elseReachable"),
                                            field(context.getType(int.class), "falseBranchFixupBci").withInitializer(UNINIT),
                                            field(context.getType(int.class), "endBranchFixupBci").withInitializer(UNINIT),
                                            field(context.getType(int.class), "child0Bci").withInitializer(UNINIT),
                                            field(context.getType(int.class), "child1Bci").withInitializer(UNINIT));
                        } else {
                            fields = List.of(//
                                            field(context.getType(boolean.class), "thenReachable"),
                                            field(context.getType(boolean.class), "elseReachable"),
                                            field(context.getType(int.class), "falseBranchFixupBci").withInitializer(UNINIT),
                                            field(context.getType(int.class), "endBranchFixupBci").withInitializer(UNINIT));
                        }
                        break;
                    case WHILE:
                        name = "WhileData";
                        fields = List.of(//
                                        field(context.getType(int.class), "whileStartBci").asFinal(),
                                        field(context.getType(boolean.class), "bodyReachable"),
                                        field(context.getType(int.class), "endBranchFixupBci").withInitializer(UNINIT));
                        break;
                    case TRY_CATCH:
                        name = "TryCatchData";
                        fields = List.of(//
                                        field(context.getType(int.class), "tryStartBci"),
                                        field(context.getType(int.class), "startStackHeight").asFinal(),
                                        field(context.getType(int.class), "exceptionLocalFrameIndex").asFinal(),
                                        field(context.getType(boolean.class), "operationReachable").asFinal(),
                                        field(context.getType(boolean.class), "tryReachable"),
                                        field(context.getType(boolean.class), "catchReachable"),
                                        field(context.getType(int.class), "endBranchFixupBci").withInitializer(UNINIT),
                                        field(arrayOf(context.getType(int.class)), "exceptionTableEntries").withInitializer("null"),
                                        field(context.getType(int.class), "exceptionTableEntryCount").withInitializer("0"));
                        methods = List.of(createAddExceptionTableEntry());
                        break;
                    case FINALLY_TRY, FINALLY_TRY_CATCH:
                        name = "FinallyTryData";
                        fields = List.of(//
                                        field(context.getDeclaredType(Runnable.class), "finallyParser").asFinal(),
                                        field(context.getType(int.class), "tryStartBci"),
                                        field(context.getType(int.class), "exceptionLocalFrameIndex").asFinal(),
                                        field(context.getType(boolean.class), "operationReachable").asFinal(),
                                        field(context.getType(boolean.class), "tryReachable"),
                                        field(context.getType(boolean.class), "catchReachable"),
                                        field(context.getType(int.class), "endBranchFixupBci").withInitializer(UNINIT),
                                        field(arrayOf(context.getType(int.class)), "exceptionTableEntries").withInitializer("null"),
                                        field(context.getType(int.class), "exceptionTableEntryCount").withInitializer("0"));

                        methods = List.of(createAddExceptionTableEntry());
                        break;
                    case FINALLY_HANDLER:
                        name = "FinallyHandlerData";
                        fields = List.of(field(context.getType(int.class), "finallyOperationSp").asFinal().withDoc(
                                        """
                                                        The index of the finally operation (FinallyTry/FinallyTryCatch) on the operation stack.
                                                        This index should only be used to skip over the handler when walking the operation stack.
                                                        It should *not* be used to access the finally operation data, because a FinallyHandler is
                                                        sometimes emitted after the finally operation has already been popped.
                                                        """));
                        break;
                    case CUSTOM, CUSTOM_INSTRUMENTATION:
                        if (operation.isTransparent()) {
                            name = "TransparentData";
                            fields = List.of(//
                                            field(context.getType(boolean.class), "producedValue").withInitializer("false"),
                                            field(context.getType(int.class), "childBci").withInitializer(UNINIT));
                        } else {
                            name = "CustomOperationData";
                            fields = List.of(//
                                            field(arrayOf(context.getType(int.class)), "childBcis").asFinal(),
                                            field(arrayOf(context.getType(int.class)), "constants").asFinal(),
                                            field(arrayOf(context.getDeclaredType(Object.class)), "locals").asFinal().asVarArgs());
                        }
                        break;
                    case CUSTOM_SHORT_CIRCUIT:
                        name = "CustomShortCircuitOperationData";
                        fields = List.of(//
                                        field(context.getType(int.class), "childBci").withInitializer(UNINIT),
                                        field(generic(List.class, Integer.class), "branchFixupBcis").withInitializer("new ArrayList<>(4)"));
                        break;
                    default:
                        if (operation.isTransparent()) {
                            name = "TransparentData";
                            fields = List.of(//
                                            field(context.getType(boolean.class), "producedValue"),
                                            field(context.getType(int.class), "childBci"));
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

            private DataClassField field(TypeMirror type, String name) {
                return new DataClassField(type, name);
            }

            private CodeExecutableElement createAddExceptionTableEntry() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), type(void.class), "addExceptionTableEntry");
                ex.addParameter(new CodeVariableElement(type(int.class), "handlerIndex"));
                CodeTreeBuilder b = ex.createBuilder();

                // handlerIndex can be UNINIT if the range is empty; don't add it.
                b.startIf().string("handlerIndex == " + UNINIT).end().startBlock();
                b.returnStatement();
                b.end();

                b.startIf().string("exceptionTableEntries == null").end().startBlock();
                b.statement("exceptionTableEntries = new int[4]");
                b.end();

                b.startElseIf().string("exceptionTableEntryCount == exceptionTableEntries.length").end().startBlock();
                b.startAssign("exceptionTableEntries");
                b.startStaticCall(type(Arrays.class), "copyOf").string("exceptionTableEntries").string("exceptionTableEntryCount * 2").end();
                b.end();
                b.end();

                b.statement("exceptionTableEntries[exceptionTableEntryCount++] = handlerIndex");

                return ex;
            }
        }

        class OperationStackEntryFactory {
            private CodeTypeElement create() {
                operationStackEntry.addAll(List.of(
                                new CodeVariableElement(Set.of(PRIVATE, FINAL), context.getType(int.class), "operation"),
                                new CodeVariableElement(Set.of(PRIVATE, FINAL), context.getType(Object.class), "data"),
                                new CodeVariableElement(Set.of(PRIVATE, FINAL), context.getType(int.class), "sequenceNumber")));

                CodeVariableElement childCount = new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "childCount");
                childCount.createInitBuilder().string("0").end();
                CodeVariableElement declaredLabels = new CodeVariableElement(Set.of(PRIVATE), generic(context.getDeclaredType(ArrayList.class), types.BytecodeLabel), "declaredLabels");
                declaredLabels.createInitBuilder().string("null").end();
                operationStackEntry.add(childCount);
                operationStackEntry.add(declaredLabels);

                operationStackEntry.add(createConstructorUsingFields(Set.of(), operationStackEntry, null, Set.of("childCount", "declaredLabels")));
                operationStackEntry.add(createAddDeclaredLabel());
                operationStackEntry.add(createToString0());
                operationStackEntry.add(createToString1());

                return operationStackEntry;
            }

            private CodeExecutableElement createAddDeclaredLabel() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), context.getType(void.class), "addDeclaredLabel");
                ex.addParameter(new CodeVariableElement(types.BytecodeLabel, "label"));

                CodeTreeBuilder b = ex.createBuilder();

                b.startIf().string("declaredLabels == null").end().startBlock();
                b.statement("declaredLabels = new ArrayList<>(8)");
                b.end();

                b.statement("declaredLabels.add(label)");

                return ex;
            }

            private CodeExecutableElement createToString0() {
                CodeExecutableElement ex = GeneratorUtils.overrideImplement(context.getDeclaredType(Object.class), "toString");
                CodeTreeBuilder b = ex.createBuilder();
                b.statement("return toString(null)");
                addOverride(ex);
                return ex;
            }

            private CodeExecutableElement createToString1() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(String.class), "toString");
                ex.addParameter(new CodeVariableElement(bytecodeBuilderType, "builder"));
                CodeTreeBuilder b = ex.createBuilder();

                b.startDeclaration(type(StringBuilder.class), "b").startNew(type(StringBuilder.class)).end().end();
                b.startStatement().startCall("b.append").doubleQuote("(").end().end();
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
                            if (model.enableLocalScoping) {
                                b.startCase().tree(createOperationConstant(op)).end().startBlock();
                                b.declaration(getDataClassName(op), "operationData", "(" + getDataClassName(op) + ") data");

                                b.startIf().string("operationData.localsCount > 0").end().startBlock();
                                b.startStatement().startCall("b.append").doubleQuote(" locals=").end().end();
                                b.startStatement().startCall("b.append").string("operationData.localsCount").end().end();
                                b.end();
                                b.end();
                                b.statement("break");
                            }
                            break;
                    }
                }
                b.end(); // switch
                b.startStatement().startCall("b.append").doubleQuote(")").end().end();

                b.statement("return b.toString()");
                return ex;
            }
        }

        class ConstantPoolFactory {
            private CodeTypeElement create() {
                List<CodeVariableElement> fields = List.of(
                                new CodeVariableElement(Set.of(PRIVATE, FINAL), generic(ArrayList.class, context.getType(Object.class)), "constants"),
                                new CodeVariableElement(Set.of(PRIVATE, FINAL), generic(HashMap.class, context.getType(Object.class), context.getDeclaredType(Integer.class)), "map"));

                constantPool.addAll(fields);

                CodeExecutableElement ctor = createConstructorUsingFields(Set.of(), constantPool, null, Set.of("constants", "map"));
                CodeTreeBuilder b = ctor.appendBuilder();
                b.statement("constants = new ArrayList<>()");
                b.statement("map = new HashMap<>()");
                constantPool.add(ctor);

                constantPool.add(createAddConstant());
                constantPool.add(createAllocateSlot());
                constantPool.add(createGetConstant());
                constantPool.add(createToArray());

                return constantPool;
            }

            private CodeExecutableElement createAddConstant() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(int.class),
                                "addConstant");
                ex.addParameter(new CodeVariableElement(context.getType(Object.class), "constant"));

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
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(int.class),
                                "allocateSlot");
                CodeTreeBuilder doc = ex.createDocBuilder();
                doc.startJavadoc();
                doc.string("Allocates a slot for a constant which will be manually added to the constant pool later.");
                doc.newLine();
                doc.end();

                CodeTreeBuilder b = ex.createBuilder();

                b.statement("int index = constants.size()");
                b.statement("constants.add(null)");
                b.startReturn().string("index").end();

                return ex;
            }

            private CodeExecutableElement createGetConstant() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(Object.class),
                                "getConstant");
                ex.addParameter(new CodeVariableElement(context.getType(int.class), "index"));
                CodeTreeBuilder b = ex.createBuilder();
                b.startReturn().string("constants.get(index)").end();

                return ex;
            }

            private CodeExecutableElement createToArray() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), new ArrayCodeTypeMirror(context.getType(Object.class)), "toArray");

                CodeTreeBuilder b = ex.createBuilder();
                b.startReturn().string("constants.toArray()").end();

                return ex;
            }
        }

        final class DeserializationStateElements implements ElementHelpers {
            final CodeTypeElement deserializationState = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "DeserializationState");

            final CodeVariableElement outer = addField(deserializationState, Set.of(PRIVATE, FINAL), deserializationState.asType(), "outer");
            final CodeVariableElement depth = addField(deserializationState, Set.of(PRIVATE, FINAL), type(int.class), "depth");
            final CodeVariableElement consts = addField(deserializationState, Set.of(PRIVATE, FINAL), generic(ArrayList.class, Object.class), "consts");
            final CodeVariableElement builtNodes = addField(deserializationState, Set.of(PRIVATE, FINAL), generic(ArrayList.class, bytecodeNodeGen.asType()), "builtNodes");
            final CodeVariableElement labels = addField(deserializationState, Set.of(PRIVATE, FINAL), generic(context.getDeclaredType(ArrayList.class), types.BytecodeLabel), "labels");
            final CodeVariableElement locals = addField(deserializationState, Set.of(PRIVATE, FINAL), generic(context.getDeclaredType(ArrayList.class), types.BytecodeLocal), "locals");
            final CodeVariableElement finallyParsers = addField(deserializationState, Set.of(PRIVATE, FINAL), generic(ArrayList.class, Runnable.class), "finallyParsers");

            final CodeExecutableElement constructor = new CodeExecutableElement(Set.of(PRIVATE), null, deserializationState.getSimpleName().toString());

            private DeserializationStateElements() {
                deserializationState.setEnclosingElement(bytecodeNodeGen);
                deserializationState.getImplements().add(types.BytecodeDeserializer_DeserializerContext);

                consts.createInitBuilder().startNew("ArrayList<>").end();
                builtNodes.createInitBuilder().startNew("ArrayList<>").end();
                labels.createInitBuilder().startNew("ArrayList<>").end();
                locals.createInitBuilder().startNew("ArrayList<>").end();
                finallyParsers.createInitBuilder().startNew("ArrayList<>").end();

                deserializationState.add(createConstructor());
                deserializationState.add(createReadBytecodeNode());
                deserializationState.add(createGetContext());
            }

            private CodeExecutableElement createConstructor() {
                constructor.addParameter(new CodeVariableElement(deserializationState.asType(), "outer"));

                CodeTreeBuilder b = constructor.createBuilder();
                b.statement("this.outer = outer");
                b.statement("this.depth = (outer == null) ? 0 : outer.depth + 1");

                return constructor;
            }

            private CodeExecutableElement createReadBytecodeNode() {
                CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeDeserializer_DeserializerContext, "readBytecodeNode");
                ex.renameArguments("buffer");
                CodeTreeBuilder b = ex.createBuilder();
                b.statement("return getContext(buffer.readShort()).builtNodes.get(buffer.readShort())");
                return ex;
            }

            private CodeExecutableElement createGetContext() {
                CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE), deserializationState.asType(), "getContext");
                method.addParameter(new CodeVariableElement(type(int.class), "targetDepth"));

                CodeTreeBuilder b = method.createBuilder();

                b.startAssert().string("targetDepth >= 0").end();

                b.declaration(deserializationState.asType(), "ctx", "this");
                b.startWhile().string("ctx.depth != targetDepth").end().startBlock();
                b.statement("ctx = ctx.outer");
                b.end();

                b.statement("return ctx");

                return method;
            }

        }

        class BytecodeLocalImplFactory {

            private CodeTypeElement create() {
                bytecodeLocalImpl.setSuperClass(generic(types.BytecodeLocal, model.templateType.asType()));
                bytecodeLocalImpl.setEnclosingElement(bytecodeNodeGen);

                bytecodeLocalImpl.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(int.class), "frameIndex"));
                bytecodeLocalImpl.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(int.class), "localIndex"));
                if (model.usesBoxingElimination()) {
                    bytecodeLocalImpl.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(int.class), "rootIndex"));
                }

                if (model.enableLocalScoping) {
                    bytecodeLocalImpl.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), scopeDataType.asType(), "scope"));
                }

                CodeExecutableElement constructor = bytecodeLocalImpl.add(createConstructorUsingFields(Set.of(), bytecodeLocalImpl, null));
                CodeTree tree = constructor.getBodyTree();
                CodeTreeBuilder b = constructor.createBuilder();
                b.startStatement().startSuperCall().staticReference(bytecodeRootNodesImpl.asType(), "VISIBLE_TOKEN").end().end();
                b.tree(tree);

                bytecodeLocalImpl.add(createGetLocalOffset());

                return bytecodeLocalImpl;
            }

            private CodeExecutableElement createGetLocalOffset() {
                CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeLocal, "getLocalOffset");
                CodeTreeBuilder b = ex.createBuilder();
                b.startReturn().string("frameIndex - USER_LOCALS_START_IDX").end();
                return ex;
            }

        }

        private SerializationStateElements serializationElements;
        private DeserializationStateElements deserializationElements;
        private CodeVariableElement serialization;

        private CodeTypeElement create() {
            addJavadoc(builder, """
                            Builder class to generate bytecode. An interpreter can invoke this class with its {@link com.oracle.truffle.api.bytecode.BytecodeParser} to generate bytecode.
                            """);

            builder.setEnclosingElement(bytecodeNodeGen);

            builder.add(uninitialized);
            uninitialized.createInitBuilder().string(-1).end();

            builder.add(new SavedStateFactory().create());
            builder.addAll(new OperationDataClassesFactory().create());
            builder.add(new OperationStackEntryFactory().create());
            builder.add(new ConstantPoolFactory().create());

            builder.add(createOperationNames());

            builder.add(new BytecodeLocalImplFactory().create());

            builder.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), bytecodeRootNodesImpl.asType(), "nodes"));
            builder.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), context.getType(CharSequence.class), "reparseReason"));
            builder.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), context.getType(boolean.class), "parseBytecodes"));
            builder.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), context.getType(int.class), "tags"));
            builder.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), context.getType(int.class), "instrumentations"));
            builder.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), context.getType(boolean.class), "parseSources"));

            builder.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), generic(ArrayList.class, bytecodeNodeGen.asType()), "builtNodes"));

            builder.add(new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "numRoots"));
            builder.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), generic(ArrayList.class, types.Source), "sources"));

            if (model.enableSerialization) {
                serializationElements = new SerializationStateElements();
                builder.add(serializationElements.serializationState);
                serialization = builder.add(new CodeVariableElement(Set.of(PRIVATE),
                                serializationElements.serializationState.asType(), "serialization"));

                deserializationElements = new DeserializationStateElements();
                builder.add(deserializationElements.deserializationState);
            }

            builder.add(createReparseConstructor());
            builder.add(createParseConstructor());

            builder.addAll(builderState);

            builder.add(createCreateLocal());
            builder.add(createCreateLocalAllParameters());
            builder.add(createCreateLabel());
            builder.add(createRegisterUnresolvedLabel());
            builder.add(createResolveUnresolvedLabel());
            builder.add(createCreateBranchLabelMapping());

            for (OperationModel operation : model.getOperations()) {
                if (omitBuilderMethods(operation)) {
                    continue;
                }

                if (operation.hasChildren()) {
                    builder.add(createBegin(operation));
                    builder.add(createEnd(operation));
                } else {
                    builder.add(createEmit(operation));
                }
            }
            builder.add(createMarkReachable());
            builder.add(createUpdateReachable());

            builder.add(createBeginOperation());
            builder.add(createEndOperation());
            builder.add(createEmitOperationBegin());
            builder.add(createBeforeChild());
            builder.add(createAfterChild());
            builder.add(createEnsureBytecodeCapacity());
            builder.add(createDoEmitVariadic());
            builder.add(createDoEmitFinallyHandler());
            builder.add(createDoCreateExceptionHandler());
            builder.add(createDoEmitSourceInfo());
            builder.add(createFinish());
            builder.add(createBeforeEmitBranch());
            builder.add(createBeforeEmitReturn());
            builder.add(createDoEmitRoot());
            builder.add(createAllocateNode());
            builder.add(createAllocateBranchProfile());
            if (model.enableYield) {
                builder.add(createAllocateContinuationConstant());

                if (model.enableTagInstrumentation) {
                    builder.add(createDoEmitTagYield());
                    builder.add(createDoEmitTagResume());
                }
            }

            if (model.enableLocalScoping) {
                builder.add(createGetCurrentScope());
            }
            builder.add(createDoEmitLocal());
            builder.add(createEnsureLocalsCapacity());

            if (model.enableSerialization) {
                builder.add(createSerialize());
                builder.add(createSerializeFinallyParser());
                builder.add(createDeserialize());
            }

            builder.add(createToString());

            builder.addAll(doEmitInstructionMethods.values());

            return builder;
        }

        private boolean omitBuilderMethods(OperationModel operation) {
            // These operations are emitted automatically. The builder methods are unnecessary.
            return (model.prolog != null && model.prolog.operation == operation) ||
                            (model.epilogExceptional != null && model.epilogExceptional.operation == operation);
        }

        private CodeExecutableElement createMarkReachable() {
            CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE),
                            context.getType(void.class), "markReachable");
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
                        case FINALLY_TRY:
                        case FINALLY_TRY_CATCH:
                            b.startCase().tree(createOperationConstant(op)).end().startBlock();
                            emitCastOperationData(b, op, "i");
                            b.startIf().string("operation.childCount == 0").end().startBlock();
                            b.statement("operationData.tryReachable = newReachable");
                            if (op.kind == OperationKind.FINALLY_TRY_CATCH) {
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
                            context.getType(boolean.class), "updateReachable");

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
                        case FINALLY_TRY:
                        case FINALLY_TRY_CATCH:
                            b.startCase().tree(createOperationConstant(op)).end().startBlock();
                            emitCastOperationData(b, op, "i");
                            b.startIf().string("operation.childCount == 0").end().startBlock();
                            b.statement("this.reachable = operationData.tryReachable");
                            if (op.kind == OperationKind.FINALLY_TRY_CATCH) {
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
                            context.getType(void.class), "serialize");
            method.addParameter(new CodeVariableElement(type(DataOutput.class), "buffer"));
            method.addParameter(new CodeVariableElement(types.BytecodeSerializer, "callback"));

            // When serializing existing BytecodeRootNodes, we want to use their field values rather
            // than the ones that get stored on the dummy root nodes during the reparse.
            TypeMirror nodeList = generic(List.class, model.getTemplateType().asType());
            CodeVariableElement existingNodes = new CodeVariableElement(nodeList, "existingNodes");
            mergeSuppressWarnings(existingNodes, "unused");
            method.addParameter(existingNodes);

            method.addThrownType(context.getType(IOException.class));
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

                b.statement("short[][] nodeFields = new short[nodesToSerialize.size()][]");
                b.startFor().string("int i = 0; i < nodeFields.length; i ++").end().startBlock();
                b.declaration(model.getTemplateType().asType(), "node", "nodesToSerialize.get(i)");
                b.statement("short[] fields = nodeFields[i] = new short[" + model.serializedFields.size() + "]");
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
                b.statement("short[] fields = nodeFields[i]");

                for (int i = 0; i < model.serializedFields.size(); i++) {
                    serializationElements.writeShort(b, "fields[" + i + "]");
                }
                b.end();
            }

            b.end().startFinallyBlock();
            b.statement("this.serialization = null");
            b.end();

            return method;

        }

        private CodeExecutableElement createSerializeFinallyParser() {
            CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE), type(short.class), "serializeFinallyParser");
            method.addParameter(new CodeVariableElement(declaredType(Runnable.class), "finallyParser"));
            method.addThrownType(declaredType(IOException.class));

            CodeTreeBuilder b = method.getBuilder();

            b.startDeclaration(declaredType(ByteArrayOutputStream.class), "baos");
            b.startNew(declaredType(ByteArrayOutputStream.class)).end();
            b.end();
            b.declaration(serializationElements.serializationState.asType(), "outerSerialization", "serialization");

            b.startTryBlock();
            b.startAssign("serialization").startNew(serializationElements.serializationState.asType());
            b.startNew(declaredType(DataOutputStream.class)).string("baos").end();
            b.string("serialization");
            b.end(2);
            b.statement("finallyParser.run()");
            serializationElements.writeShort(b, serializationElements.codeEndFinallyParser);
            b.end(); // try

            b.startFinallyBlock();
            b.statement("serialization = outerSerialization");
            b.end(); // finally

            b.declaration(arrayOf(type(byte.class)), "bytes", "baos.toByteArray()");
            serializationElements.writeShort(b, serializationElements.codeCreateFinallyParser);
            serializationElements.writeInt(b, "bytes.length");
            serializationElements.writeBytes(b, "bytes");
            b.statement("return (short) serialization.finallyParserCount++");

            return method;
        }

        private CodeExecutableElement createDeserialize() {
            CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE),
                            context.getType(void.class), "deserialize");
            mergeSuppressWarnings(method, "hiding");

            method.addParameter(new CodeVariableElement(types.TruffleLanguage, "language"));
            method.addParameter(new CodeVariableElement(generic(Supplier.class, DataInput.class), "bufferSupplier"));
            method.addParameter(new CodeVariableElement(types.BytecodeDeserializer, "callback"));
            method.addParameter(new CodeVariableElement(deserializationElements.deserializationState.asType(), "outerContext"));

            CodeTreeBuilder b = method.createBuilder();

            b.startTryBlock();

            b.startDeclaration(deserializationElements.deserializationState.asType(), "context");
            b.startNew(deserializationElements.deserializationState.asType()).string("outerContext").end();
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
            b.statement("int nameId = buffer.readShort()");
            b.statement("Object name = null");
            b.startIf().string("nameId != -1").end().startBlock();
            b.statement("name = context.consts.get(nameId)");
            b.end();
            b.statement("int infoId = buffer.readShort()");
            b.statement("Object info = null");
            b.startIf().string("infoId != -1").end().startBlock();
            b.statement("info = context.consts.get(infoId)");
            b.end();
            b.statement("context.locals.add(createLocal(name, info))");
            b.statement("break");
            b.end(); // create local

            b.startCase().staticReference(serializationElements.codeCreateObject).end().startBlock();
            b.statement("context.consts.add(callback.deserialize(context, buffer))");
            b.statement("break");
            b.end(); // create object

            b.startCase().staticReference(serializationElements.codeCreateFinallyParser).end().startBlock();
            b.statement("byte[] finallyParserBytes = new byte[buffer.readInt()]");
            b.statement("buffer.readFully(finallyParserBytes)");

            b.startStatement().startCall("context.finallyParsers.add");
            b.startGroup().string("() -> ").startCall("deserialize");
            b.string("language");
            b.startGroup().string("() -> ").startStaticCall(types.SerializationUtils, "createDataInput");
            b.startStaticCall(declaredType(ByteBuffer.class), "wrap").string("finallyParserBytes").end();
            b.end(2);
            b.string("callback");
            b.string("context");
            b.end(2); // lambda
            b.end(2);
            b.statement("break");
            b.end(); // create finally parser

            b.startCase().staticReference(serializationElements.codeEndFinallyParser).end().startBlock();
            b.statement("return");
            b.end(); // end finally parser

            b.startCase().staticReference(serializationElements.codeEndSerialize).end().startBlock();

            if (model.serializedFields.size() != 0) {
                b.startFor().string("int i = 0; i < this.builtNodes.size(); i++").end().startBlock();
                b.declaration(bytecodeNodeGen.asType(), "node", "this.builtNodes.get(i)");
                for (int i = 0; i < model.serializedFields.size(); i++) {
                    VariableElement var = model.serializedFields.get(i);
                    b.startStatement();
                    b.string("node.").string(var.getSimpleName().toString());
                    b.string(" = ");
                    if (ElementUtils.needsCastTo(type(Object.class), var.asType())) {
                        b.cast(var.asType());
                    }
                    b.string("context.consts.get(buffer.readShort())");
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
                    b.startThrow().startNew(context.getType(IllegalStateException.class));
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
                        b.type(bytecodeNodeGen.asType()).string(" node = ").cast(bytecodeNodeGen.asType()).string("end" + operation.name + "()");
                        b.end();
                        b.startAssert().string("context.").variable(deserializationElements.depth).string(" == buffer.readShort()").end();
                        b.startStatement().startCall("context.builtNodes.set").string("buffer.readShort()").string("node").end().end();
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
            b.startThrow().startNew(context.getType(IllegalStateException.class));
            b.startGroup();
            b.doubleQuote("Unknown operation code ").string(" + code");
            b.end();
            b.end().end();

            b.end(); // switch block
            b.end();

            b.end(); // switch
            b.end(); // while block

            b.end().startCatchBlock(context.getType(IOException.class), "ex");
            b.startThrow().startNew(context.getType(IOError.class)).string("ex").end(2);
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
                    serializationElements.writeShort(after, "(short) ((BytecodeLocalImpl) " + argumentName + ").frameIndex /* context depth */");
                    serializationElements.writeShort(after, "(short) ((BytecodeLocalImpl) " + argumentName + ").localIndex /* local index */");
                    break;
                case LOCAL_ARRAY:
                    serializationElements.writeShort(after, "(short) " + argumentName + ".length");
                    String depth = argumentName + "Depth";
                    after.startIf().string(argumentName, ".length > 0").end().startBlock();
                    after.startDeclaration(type(short.class), depth);
                    after.cast(type(short.class));
                    after.startParantheses().cast(bytecodeLocalImpl.asType()).string(argumentName, "[0]").end();
                    after.string(".frameIndex /* context depth */");
                    after.end();

                    serializationElements.writeShort(after, depth);

                    after.startFor().string("int i = 0; i < " + argumentName + ".length; i++").end().startBlock();
                    after.startDeclaration(bytecodeLocalImpl.asType(), "localImpl");
                    after.cast(bytecodeLocalImpl.asType()).string(argumentName, "[i]");
                    after.end();

                    after.startAssert().string(depth, " == (short) localImpl.frameIndex /* context depth */").end();
                    serializationElements.writeShort(after, "(short) localImpl.localIndex /* local index */");

                    after.end(); // for
                    after.end(); // if
                    break;
                case LABEL:
                    serializationElements.writeShort(after, "(short) ((BytecodeLabelImpl) " + argumentName + ").bci /* context depth */");
                    serializationElements.writeShort(after, "(short) ((BytecodeLabelImpl) " + argumentName + ").declaringOp /* label index */");
                    break;
                case TAGS:
                    serializationElements.writeInt(after, "encodedTags");
                    break;
                case INTEGER:
                    serializationElements.writeInt(after, argumentName);
                    break;
                case OBJECT: {
                    String index = argumentName + "_index";
                    before.statement("short ", index, " = ", "serialization.serializeObject(", argumentName, ")");
                    serializationElements.writeShort(after, index);
                    break;
                }
                case FINALLY_PARSER: {
                    String index = "finallyParserIndex";
                    before.startDeclaration(type(short.class), index);
                    before.startCall("serializeFinallyParser");
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
                case INTEGER:
                    b.declaration(argType, argumentName, "buffer.readInt()");
                    break;
                case LOCAL_ARRAY:
                    b.startDeclaration(argType, argumentName).startNewArray(arrayOf(types.BytecodeLocal), CodeTreeBuilder.singleString("buffer.readShort()")).end().end();
                    b.declaration(deserializationElements.deserializationState.asType(), "setterContext", "context.getContext(buffer.readShort())");
                    b.startFor().string("int i = 0; i < ", argumentName, ".length; i++").end().startBlock();
                    b.statement(argumentName, "[i] = setterContext.locals.get(buffer.readShort())");
                    b.end();
                    break;
                case OBJECT:
                    b.startDeclaration(argType, argumentName);
                    if (!ElementUtils.isObject(argType)) {
                        b.cast(argType);
                    }
                    b.string("context.consts.get(buffer.readShort())");
                    b.end(); // declaration
                    break;
                case FINALLY_PARSER:
                    b.startDeclaration(argType, argumentName);
                    b.string("context.getContext(buffer.readShort()).finallyParsers.get(buffer.readShort())");
                    b.end();
                    break;
                default:
                    throw new AssertionError("unexpected argument kind " + argument.kind());
            }
        }

        private CodeExecutableElement createFinish() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "finish");
            CodeTreeBuilder b = ex.createBuilder();

            b.startIf().string("reparseReason == null").end().startBlock();
            b.startStatement().string("nodes.setNodes(builtNodes.toArray(new ").type(bytecodeNodeGen.asType()).string("[0]))").end();
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
                    b.declaration(type(short.class), "nameId");
                    b.startIf().string("name != null").end().startBlock();
                    b.statement("nameId = serialization.serializeObject(name)");
                    b.end().startElseBlock();
                    b.statement("nameId = -1");
                    b.end();

                    b.declaration(type(short.class), "infoId");
                    b.startIf().string("info != null").end().startBlock();
                    b.statement("infoId = serialization.serializeObject(info)");
                    b.end().startElseBlock();
                    b.statement("infoId = -1");
                    b.end();

                    serializationElements.writeShort(b, serializationElements.codeCreateLocal);
                    serializationElements.writeShort(b, "nameId");
                    serializationElements.writeShort(b, "infoId");
                });
                b.startReturn();

                b.startNew(bytecodeLocalImpl.asType());
                // Stuff the data needed for serialization into the locals' fields
                b.string("serialization.depth");
                b.string("serialization.localCount++");
                if (model.usesBoxingElimination()) {
                    b.string("-1");
                }
                if (model.enableLocalScoping) {
                    b.string("null");
                }
                b.end(); // new

                b.end();
                b.end();
            }

            if (model.enableLocalScoping) {
                TypeMirror scopeType = scopeDataType.asType();
                b.declaration(scopeType, "scope", "getCurrentScope()");
                b.declaration(type(int.class), "scopeLocalIndex", "scope.localsCount++");
                b.declaration(type(int.class), "frameIndex", "USER_LOCALS_START_IDX + scope.frameOffset + scopeLocalIndex");
                b.declaration(type(int.class), "localIndex", "numLocals++");
                b.declaration(type(int.class), "tableIndex", "doEmitLocal(name, info, localIndex, frameIndex)");

                b.startIf().string("scope.locals == null").end().startBlock();
                b.startAssign("scope.locals").startNewArray(arrayOf(type(int.class)), CodeTreeBuilder.singleString("8")).end().end();
                b.end();
                b.startElseIf().string("scopeLocalIndex >= scope.locals.length").end().startBlock();
                b.startAssign("scope.locals").startStaticCall(context.getType(Arrays.class), "copyOf");
                b.string("scope.locals");
                b.string("scope.locals.length * 2");
                b.end(2); // assign, static call
                b.end(); // if block
                b.statement("scope.locals[scopeLocalIndex] = tableIndex");
            } else {
                b.declaration(type(int.class), "frameIndex", "USER_LOCALS_START_IDX + numLocals");
                b.declaration(type(int.class), "localIndex", "numLocals++");
                b.declaration(type(int.class), "tableIndex", "doEmitLocal(name, info, localIndex)");
            }

            b.startDeclaration(bytecodeLocalImpl.asType(), "local");

            b.startNew(bytecodeLocalImpl.asType()).string("frameIndex");

            if (model.enableLocalScoping) {
                b.string("localIndex");
            } else {
                b.string("frameIndex");
            }
            if (model.usesBoxingElimination()) {
                b.string("((RootData)operationStack[this.rootOperationSp].data).index");
            }
            if (model.enableLocalScoping) {
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
            b.startThrow().startNew(type(IllegalStateException.class)).doubleQuote("Invalid scope for local variable.").end().end();
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

                b.startReturn().startNew(bytecodeLabelImpl.asType());
                b.string("numLabels++");
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
            emitThrowIllegalStateException(b, "\"Labels must be created inside either Block or Root operations.\"");
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

        private CodeExecutableElement createRegisterUnresolvedLabel() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "registerUnresolvedLabel");
            ex.addParameter(new CodeVariableElement(types.BytecodeLabel, "label"));
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "immediateBci"));

            CodeTreeBuilder b = ex.createBuilder();
            b.declaration(generic(context.getDeclaredType(ArrayList.class), context.getDeclaredType(Integer.class)), "locations", "unresolvedLabels.computeIfAbsent(label, k -> new ArrayList<>())");
            b.startStatement().startCall("locations.add");
            b.string("immediateBci");
            b.end(2);

            return ex;
        }

        private CodeExecutableElement createResolveUnresolvedLabel() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "resolveUnresolvedLabel");
            ex.addParameter(new CodeVariableElement(types.BytecodeLabel, "label"));
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "stackHeight"));

            CodeTreeBuilder b = ex.createBuilder();

            b.statement("BytecodeLabelImpl impl = (BytecodeLabelImpl) label");
            b.statement("assert !impl.isDefined()");
            b.statement("impl.bci = bci");
            b.declaration(generic(List.class, context.getDeclaredType(Integer.class)), "sites", "unresolvedLabels.remove(impl)");
            b.startIf().string("sites != null").end().startBlock();
            b.startFor().startGroup().type(context.getDeclaredType(Integer.class)).string(" site : sites").end(2).startBlock();

            b.statement(writeBc("site", "(short) impl.bci"));
            b.end(2);

            return ex;
        }

        private CodeExecutableElement createCreateBranchLabelMapping() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), generic(HashMap.class, context.getDeclaredType(Integer.class), types.BytecodeLabel),
                            "createBranchLabelMapping");
            ex.addParameter(new CodeVariableElement(unresolvedLabelsType, "unresolvedLabels"));

            CodeTreeBuilder b = ex.createBuilder();
            b.statement("HashMap<Integer, BytecodeLabel> result = new HashMap<>()");
            b.startFor().string("BytecodeLabel lbl : unresolvedLabels.keySet()").end().startBlock();
            b.startFor().startGroup().type(context.getDeclaredType(Integer.class)).string(" site : unresolvedLabels.get(lbl)").end(2).startBlock();
            b.statement("assert !result.containsKey(site)");
            b.statement("result.put(site, lbl)");
            b.end(2);
            b.startReturn().string("result").end();

            return ex;
        }

        private CodeVariableElement createOperationNames() {
            CodeVariableElement fld = new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), context.getType(String[].class), "OPERATION_NAMES");

            CodeTreeBuilder b = fld.createInitBuilder();
            b.startNewArray((ArrayType) context.getType(String[].class), null);
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
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "beginOperation");
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "id"));
            ex.addParameter(new CodeVariableElement(context.getType(Object.class), "data"));

            CodeTreeBuilder b = ex.createBuilder();

            b.startIf().string("operationSp == operationStack.length").end().startBlock(); // {
            b.startAssign("operationStack").startStaticCall(context.getType(Arrays.class), "copyOf");
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
                        sb.append("{@link #");
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
            } else if (operation.kind == OperationKind.CUSTOM) {
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
            assert operation.hasChildren();

            List<String> lines = new ArrayList<>(1);
            lines.add(getBuilderMethodJavadocHeader("Ends", operation));
            lines.add("<p>");
            lines.add(getOperationSignatureJavadoc(operation));

            if (model.epilogReturn != null && operation == model.epilogReturn.operation) {
                lines.add("<p>");
                lines.add(String.format(
                                "NB: This method does not directly emit %s instructions. Instead, {@link #beforeEmitReturn} uses the operation stack entry to determine that each Return should be preceded by a %s instruction.",
                                operation.name, operation.name));
            } else if (operation.kind == OperationKind.TAG) {
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
            assert rootOperation.kind == OperationKind.ROOT;

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
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(visibility), context.getType(void.class), "begin" + operation.name);

            for (OperationArgument arg : operation.operationBeginArguments) {
                ex.addParameter(arg.toVariableElement());
            }
            ex.setVarArgs(operation.operationBeginArgumentVarArgs);
            addBeginOrEmitOperationDoc(operation, ex);

            CodeTreeBuilder b = ex.createBuilder();

            if (operation.kind == OperationKind.TAG) {
                b.startIf().string("newTags.length == 0").end().startBlock();
                b.startThrow().startNew(type(IllegalArgumentException.class)).doubleQuote("The tags parameter for beginTag must not be empty. Please specify at least one tag.").end().end();
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
                b.declaration(tagNode.asType(), "node", "new TagNode(encodedTags & this.tags, (short)bci)");
                b.startIf().string("tagNodes == null").end().startBlock();
                b.statement("tagNodes = new ArrayList<>()");
                b.end();
                b.declaration(type(int.class), "nodeId", "tagNodes.size()");
                b.statement("tagNodes.add(node)");
            }

            if (operation.requiresRootOperation()) {
                b.startStatement().startCall("validateRootOperationBegin").end(2);
            }

            switch (operation.kind) {
                case ROOT:
                case BLOCK:
                    if (model.enableLocalScoping) {
                        b.declaration(scopeDataType.asType(), "parentScope", "getCurrentScope()");
                    }
                    break;
                case STORE_LOCAL:
                case STORE_LOCAL_MATERIALIZED:
                case LOAD_LOCAL:
                case LOAD_LOCAL_MATERIALIZED:
                    if (model.enableLocalScoping) {
                        createThrowInvalidScope(b, operation);
                    }
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
                    if (model.enableLocalScoping) {
                        b.statement("operationData.frameOffset = parentScope.frameOffset + parentScope.localsCount");
                    }
                    break;
                case WHILE:
                    if (model.enableTracing) {
                        b.statement("basicBlockBoundary[bci] = true");
                    }
                    break;
                case RETURN:
                    break;
                case TAG:
                    buildEmitInstruction(b, model.tagEnterInstruction, "nodeId");
                    break;
                case FINALLY_TRY:
                case FINALLY_TRY_CATCH:
                    if (model.enableTracing) {
                        b.statement("basicBlockBoundary[0] = true");
                    }
                    break;
            }

            return ex;
        }

        private CodeExecutableElement validateScope;

        private CodeExecutableElement getValidateScope() {
            if (validateScope != null) {
                return validateScope;
            }

            CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(void.class), "validateLocalScope");
            method.addParameter(new CodeVariableElement(types.BytecodeLocal, "local"));

            CodeTreeBuilder b = method.createBuilder();
            b.startIf().string("!(").cast(bytecodeLocalImpl.asType()).string("local").string(").scope.valid").end().startBlock();
            b.startThrow().startNew(type(IllegalArgumentException.class)).doubleQuote("Local variable scope of this local no longer valid.").end().end();
            b.end();

            builder.add(method);

            validateScope = method;
            return method;
        }

        private void createThrowInvalidScope(CodeTreeBuilder b, OperationModel operation) {
            b.startStatement().startCall(getValidateScope().getSimpleName().toString()).string(operation.getOperationBeginArgumentName(0)).end().end();
        }

        private CodeExecutableElement createBeginRoot(OperationModel rootOperation) {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), context.getType(void.class), "beginRoot");
            ex.addParameter(new CodeVariableElement(types.TruffleLanguage, "language"));
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
            if (model.enableLocalScoping) {
                b.statement("maxLocals = numLocals");
            }
            b.statement("numLabels = 0");
            b.statement("numNodes = 0");
            b.statement("numConditionalBranches = 0");
            b.statement("constantPool = new ConstantPool()");

            b.statement("bc = new short[32]");
            b.statement("bci = 0");
            b.statement("currentStackHeight = 0");
            b.statement("maxStackHeight = 0");
            b.statement("exHandlers = new int[10]");
            b.statement("exHandlerCount = 0");
            b.statement("unresolvedLabels = new HashMap<>()");
            if (model.enableTracing) {
                b.statement("basicBlockBoundary = new boolean[33]");
            }
            if (model.enableYield) {
                b.statement("continuationLocations = new ArrayList<>()");
            }
            b.startIf().string("parseSources").end().startBlock();
            b.statement("sourceInfo = new int[16]");
            b.statement("sourceInfoIndex = 0");
            b.end();

            b.startStatement().string("RootData operationData = ");
            b.tree(createOperationData("RootData", "language", "numRoots++"));
            b.end();
            b.startIf().string("reparseReason == null").end().startBlock();
            b.statement("builtNodes.add(null)");
            b.end();

            if (model.enableLocalScoping) {
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
            VariableElement existing = builder.findField(name);
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

            builder.add(newVariable);
            return newVariable;

        }

        private void createSerializeBegin(OperationModel operation, CodeTreeBuilder b) {
            serializationWrapException(b, () -> {

                if (operation.kind == OperationKind.ROOT) {
                    b.startDeclaration(bytecodeNodeGen.asType(), "node");
                    b.startNew(bytecodeNodeGen.asType());
                    b.string("serialization.language");
                    b.startStaticCall(types.FrameDescriptor, "newBuilder").end();
                    b.string("null"); // BytecodeRootNodesImpl
                    b.string("0"); // numLocals
                    b.string("serialization.nextBuildIndex()"); // buildIndex
                    for (VariableElement var : ElementFilter.fieldsIn(abstractBytecodeNode.getEnclosedElements())) {
                        b.defaultValue(var.asType());
                    }
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
                case LOAD_LOCAL_MATERIALIZED, LOAD_LOCAL -> {
                    yield CodeTreeBuilder.singleString("(BytecodeLocalImpl)" + operation.getOperationBeginArgumentName(0));
                }
                case IF_THEN -> createOperationData(className, "this.reachable");
                case IF_THEN_ELSE -> createOperationData(className, "this.reachable", "this.reachable");
                case CONDITIONAL -> createOperationData(className, "this.reachable", "this.reachable");
                case WHILE -> createOperationData(className, "bci", "this.reachable");
                case TRY_CATCH -> createOperationData(className, "bci", "currentStackHeight", "((BytecodeLocalImpl) " + operation.getOperationBeginArgumentName(0) + ").frameIndex",
                                "this.reachable", "this.reachable", "this.reachable");
                case FINALLY_TRY, FINALLY_TRY_CATCH -> {
                    assert operation.operationBeginArguments[0].kind() == Encoding.LOCAL;
                    assert operation.operationBeginArguments[1].kind() == Encoding.FINALLY_PARSER;
                    String exceptionLocal = CodeTreeBuilder.createBuilder() //
                                    .startParantheses() //
                                    .cast(bytecodeLocalImpl.asType()).string(operation.getOperationBeginArgumentName(0)) //
                                    .end() //
                                    .string(".frameIndex").toString();
                    String catchReachable = (operation.kind == OperationKind.FINALLY_TRY_CATCH) ? "this.reachable" : "false";

                    yield createOperationData(className, operation.getOperationBeginArgumentName(1), "bci", exceptionLocal, "this.reachable", "this.reachable", catchReachable);
                }
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
                            childBciArrayBuilder.startNewArray(arrayOf(context.getType(int.class)), null);
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
                            constantsArrayBuilder.startNewArray(arrayOf(context.getType(int.class)), null);
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
                    b.startThrow().startNew(type(IllegalArgumentException.class)).doubleQuote("Byte-based sources are not supported.").end(2);
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
                    buildOperationStackWalk(b, () -> {
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
                    emitThrowIllegalStateException(b, "\"No enclosing Source operation found - each SourceSection must be enclosed in a Source operation.\"");
                    b.end();

                    b.declaration(type(int.class), "beginBci");
                    b.startIf().string("rootOperationSp == -1").end().startBlock();
                    b.lineComment("not in a root yet");
                    b.statement("beginBci = 0");
                    b.end().startElseBlock();
                    b.statement("beginBci = bci");
                    b.end();

                    yield createOperationData(className, "foundSourceIndex", "beginBci", operation.getOperationBeginArgumentName(0), operation.getOperationBeginArgumentName(1));
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
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "id"));

            CodeTreeBuilder b = ex.createBuilder();

            b.startIf().string("operationSp == 0").end().startBlock(); // {
            b.startThrow().startNew(context.getType(IllegalStateException.class));
            b.string("\"Unexpected operation end - there are no operations on the stack. Did you forget a beginRoot()?\"").end();
            b.end(2);
            b.end(); // }

            b.statement("OperationStackEntry entry = operationStack[operationSp - 1]");

            b.startIf().string("entry.operation != id").end().startBlock(); // {
            b.startThrow().startNew(context.getType(IllegalStateException.class));
            b.string("\"Unexpected operation end, expected end\" + OPERATION_NAMES[entry.operation] + \", but got end\" + OPERATION_NAMES[id]").end();
            b.end(2);
            b.end(); // }

            b.startIf().string("entry.declaredLabels != null").end().startBlock();
            b.startFor().string("BytecodeLabel label : entry.declaredLabels").end().startBlock();
            b.statement("BytecodeLabelImpl impl = (BytecodeLabelImpl) label");
            b.startIf().string("!impl.isDefined()").end().startBlock();
            b.startThrow().startNew(context.getType(IllegalStateException.class));
            b.string("\"Operation \" + OPERATION_NAMES[id] + \" ended without emitting one or more declared labels. This likely indicates a bug in the parser.\"").end();
            b.end(2);
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
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(visibility), context.getType(void.class), "end" + operation.name);

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
                b.startThrow().startNew(type(IllegalArgumentException.class)).doubleQuote("The tags parameter for beginTag must not be empty. Please specify at least one tag.").end().end();
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
                emitThrowIllegalStateException(b, "\"Operation " + operation.name + " expected at least " + childString(1) +
                                ", but \" + operation.childCount + \" provided. This is probably a bug in the parser.\"");
                b.end();
            } else if (operation.isVariadic && operation.numDynamicOperands() > 1) {
                // The variadic child is included in numChildren, so the operation requires
                // numChildren - 1 children at minimum.
                b.startIf().string("operation.childCount < " + (operation.numDynamicOperands() - 1)).end().startBlock();
                emitThrowIllegalStateException(b, "\"Operation " + operation.name + " expected at least " + childString(operation.numDynamicOperands() - 1) +
                                ", but \" + operation.childCount + \" provided. This is probably a bug in the parser.\"");
                b.end();
            } else if (!operation.isVariadic) {
                b.startIf().string("operation.childCount != " + operation.numDynamicOperands()).end().startBlock();
                emitThrowIllegalStateException(b, "\"Operation " + operation.name + " expected exactly " + childString(operation.numDynamicOperands()) +
                                ", but \" + operation.childCount + \" provided. This is probably a bug in the parser.\"");
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
                    if (model.enableTracing) {
                        b.statement("basicBlockBoundary[bci] = true");
                    }
                    // Go through the work list and fill in the branch target for each branch.
                    b.startFor().string("int site : operationData.branchFixupBcis").end().startBlock();
                    b.statement(writeBc("site", "(short) bci"));
                    b.end();
                    break;
                case SOURCE_SECTION:
                    b.startStatement().startCall("doEmitSourceInfo");
                    b.string("operationData.sourceIndex");
                    b.string("operationData.beginBci");
                    b.string("bci");
                    b.string("operationData.start");
                    b.string("operationData.length");
                    b.end(2);
                    break;
                case SOURCE:
                    break;
                case IF_THEN_ELSE:
                    if (model.enableTracing) {
                        b.statement("basicBlockBoundary[bci] = true");
                    }
                    emitCastCurrentOperationData(b, operation);
                    b.statement("markReachable(operationData.thenReachable || operationData.elseReachable)");
                    break;
                case IF_THEN:
                case WHILE:
                    if (model.enableTracing) {
                        b.statement("basicBlockBoundary[bci] = true");
                    }
                    b.statement("updateReachable()");
                    break;
                case CONDITIONAL:
                    emitCastCurrentOperationData(b, operation);
                    if (model.enableTracing) {
                        b.statement("basicBlockBoundary[bci] = true");
                    }
                    b.statement("markReachable(operationData.thenReachable || operationData.elseReachable)");
                    if (model.usesBoxingElimination()) {
                        buildEmitInstruction(b, operation.instruction, emitMergeConditionalArguments(operation.instruction));
                    }
                    break;
                case TRY_CATCH:
                    emitCastCurrentOperationData(b, operation);
                    b.statement("markReachable(operationData.tryReachable || operationData.catchReachable)");
                    break;
                case FINALLY_TRY:
                    emitCastCurrentOperationData(b, operation);
                    emitFinallyHandlersAfterTry(b, operation, "operationSp");
                    emitFixFinallyBranchBci(b);
                    b.statement("markReachable(operationData.tryReachable)");
                    break;
                case FINALLY_TRY_CATCH:
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
                    emitThrow(b, IllegalArgumentException.class, "\"The tags provided to endTag do not match the tags provided to the corresponding beginTag call.\"");
                    b.end();

                    // If this tag operation is nested in another, add it to the outer tag tree
                    b.declaration(type(int.class), "sp", "operationSp - 1");
                    b.startWhile().string("sp >= 0").end().startBlock();
                    b.startIf().string("operationStack[sp].data instanceof TagOperationData t").end().startBlock();
                    b.startIf().string("t.children == null").end().startBlock();
                    b.statement("t.children = new ArrayList<>(3)");
                    b.end();
                    b.statement("t.children.add(tagNode)");
                    b.statement("break");
                    b.end(); // if
                    b.statement("sp--");
                    b.end(); // while

                    // Otherwise, this tag is the root of a tag tree.
                    b.startIf().string("sp < 0").end().startBlock();
                    b.lineComment("not found");
                    b.startIf().string("tagRoots == null").end().startBlock();
                    b.statement("tagRoots = new ArrayList<>(3)");
                    b.end();
                    b.statement("tagRoots.add(tagNode)");
                    b.end(); // while

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
                    b.statement("tagNode.leaveBci = (short)bci");

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
                    b.statement("doCreateExceptionHandler(tagNode.enterBci, bci,  operationData.nodeId, operationData.startStackHeight, HANDLER_TAG_EXCEPTIONAL)");
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
                    b.statement("doCreateExceptionHandler(tagNode.enterBci, bci,  operationData.nodeId, operationData.startStackHeight, HANDLER_TAG_EXCEPTIONAL)");
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
                    if (model.enableLocalScoping) {
                        // with local scoping locals are emitted at the end of the block
                        // and also for roots. with global scoping this step is not necessary.
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
                b.startStatement().startCall("afterChild");
                b.string("true");
                if (operation.instruction.shortCircuitModel.returnConvertedBoolean()) {
                    // child bci is location of boolean converter instruction
                    b.string("bci - " + operation.instruction.shortCircuitModel.booleanConverterInstruction().getInstructionLength());
                } else {
                    // child bci is location of instruction producing "fall through" value
                    b.string("operationData.childBci");
                }
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
            b.startIf().string("operationData.locals != null").end().startBlock();
            b.statement("maxLocals = Math.max(maxLocals, operationData.frameOffset + operationData.localsCount)");
            b.startFor().string("int index = 0; index < operationData.localsCount; index++").end().startBlock();
            b.statement("locals[operationData.locals[index] + LOCALS_OFFSET_END_BCI] = bci");
            if (!isRoot) {
                buildEmitInstruction(b, model.clearLocalInstruction, "locals[operationData.locals[index] + LOCALS_OFFSET_INDEX]");
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
                    b.startDeclaration(bytecodeNodeGen.asType(), "result");
                    b.string("serialization.", serializationElements.rootStack.getSimpleName().toString(), ".pop()");
                    b.end();
                    serializationElements.writeInt(b, CodeTreeBuilder.singleString("result.buildIndex"));
                    b.statement("return result");
                });
                b.end();
            }

            if (needsRootBlock()) {
                emitCastOperationData(b, model.blockOperation, "operationSp - 1", "blockOperation");
                b.startIf().string("!blockOperation.producedValue").end().startBlock();
                buildEmit(b, model.loadConstantOperation, "null");
                b.end();
                buildEnd(b, model.blockOperation);
                emitCastOperationData(b, model.rootOperation, "rootOperationSp");
            } else {
                emitCastOperationData(b, model.rootOperation, "rootOperationSp");
                b.startIf().string("!operationData.producedValue").end().startBlock();
                buildEmit(b, model.loadConstantOperation, "null");
                b.end();
            }

            if (model.prolog != null || model.epilogExceptional != null || model.epilogReturn != null) {
                if (model.prolog != null) {
                    // Patch the end constants.
                    OperationModel prologOperation = model.prolog.operation;
                    List<InstructionImmediate> constantOperands = prologOperation.instruction.getImmediates(ImmediateKind.CONSTANT);
                    int endConstantsOffset = prologOperation.constantOperands.before().size();
                    for (int i = 0; i < prologOperation.operationEndArguments.length; i++) {
                        InstructionImmediate immediate = constantOperands.get(endConstantsOffset + i);
                        b.statement(writeBc("operationData.prologBci + " + immediate.offset(), "(short) constantPool.addConstant(" + prologOperation.operationEndArguments[i].name() + ")"));
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
                    b.statement("doCreateExceptionHandler(0, bci, -1, -1, HANDLER_EPILOG_EXCEPTIONAL)");
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

            if (model.enableLocalScoping) {
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
            b.startAssign("handlers_").startStaticCall(type(Arrays.class), "copyOf").string("exHandlers").string("exHandlerCount").end().end();
            b.startAssign("sources_").string("sources").end();
            b.startAssign("numNodes_").string("numNodes").end();
            b.startAssign("locals_").string("locals == null ? " + EMPTY_INT_ARRAY + " : ").startStaticCall(type(Arrays.class), "copyOf").string("locals").string(
                            "numLocals * LOCALS_LENGTH").end().end();
            b.end();

            if (model.enableTagInstrumentation) {
                b.startIf().string("tags != 0 && this.tagNodes != null").end().startBlock();
                b.startDeclaration(arrayOf(tagNode.asType()), "tagNodes_").string("this.tagNodes.toArray(TagNode[]::new)").end();

                b.declaration(tagNode.asType(), "tagTree_");
                b.startIf().string("this.tagRoots == null || this.tagRoots.isEmpty()").end().startBlock();
                b.startAssign("tagTree_").string("null").end();
                b.end().startElseIf().string("this.tagRoots.size() == 1").end().startBlock();
                b.startAssign("tagTree_").string("this.tagRoots.get(0)").end();
                b.end().startElseBlock();
                b.startAssign("tagTree_").startNew(tagNode.asType());
                b.string("0").string("(short) -1");
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

            b.declaration(bytecodeNodeGen.asType(), "result", (CodeTree) null);
            b.startIf().string("reparseReason != null").end().startBlock(); // {
            b.statement("result = builtNodes.get(operationData.index)");

            if (model.enableYield) {
                b.declaration(abstractBytecodeNode.asType(), "oldBytecodeNode", "result.bytecode");
            }

            b.startIf().string("parseBytecodes").end().startBlock();
            b.statement("assert result.numLocals == " + maxLocals());
            b.statement("assert result.nodes == this.nodes");
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

                b.startStatement().startCall("ACCESS.objectArrayWrite");
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

            b.startStatement().startCall("frameDescriptorBuilder.addSlots");
            b.startGroup();
            buildFrameSize(b);
            b.end();
            b.staticReference(types.FrameSlotKind, "Illegal");
            b.end(2);
            b.declaration(types.TruffleLanguage, "language", "operationData.language");

            b.startAssign("result").startNew(bytecodeNodeGen.asType());
            b.string("language");
            b.string("frameDescriptorBuilder");
            b.string("nodes"); // BytecodeRootNodesImpl
            b.string(maxLocals());
            b.string("operationData.index");

            for (VariableElement e : ElementFilter.fieldsIn(abstractBytecodeNode.getEnclosedElements())) {
                b.string(e.getSimpleName().toString() + "_");
            }
            b.end(2);

            if (model.enableYield) {
                b.declaration(types.BytecodeNode, "bytecodeNode", "result.getBytecodeNode()");

                b.startFor().type(continuationLocation.asType()).string(" continuationLocation : continuationLocations").end().startBlock();
                b.declaration(type(int.class), "constantPoolIndex", "continuationLocation.constantPoolIndex");
                b.startDeclaration(continuationRootNodeImpl.asType(), "continuationRootNode").startNew(continuationRootNodeImpl.asType());
                b.string("language");
                b.string("result.getFrameDescriptor()");
                b.string("result");
                b.string("continuationLocation.sp");
                b.startCall("bytecodeNode.getBytecodeLocation").string("continuationLocation.bci").end();
                b.end(2);

                b.startStatement().startCall("ACCESS.objectArrayWrite");
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
            if (model.enableLocalScoping) {
                return "maxLocals + USER_LOCALS_START_IDX";
            } else {
                return "numLocals + USER_LOCALS_START_IDX";
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
         * FinallyTry/FinallyTryCatch operation.
         *
         * The supplied Runnable contains the loop body and can use "i" to reference the current
         * index.
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

        private void buildOperationStackWalk(CodeTreeBuilder b, Runnable r) {
            buildOperationStackWalk(b, "0", r);
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
         * For FinallyTry, emits both regular and exceptional handlers; for FinallyTryCatch, just
         * emits the regular handler.
         */
        private void emitFinallyHandlersAfterTry(CodeTreeBuilder b, OperationModel op, String finallyHandlerSp) {
            b.declaration(type(short.class), "exceptionIndex", "(short) operationData.exceptionLocalFrameIndex");
            b.declaration(type(int.class), "handlerSp", "currentStackHeight");
            b.declaration(type(int.class), "exHandlerIndex", UNINIT);

            b.startIf().string("operationData.operationReachable").end().startBlock();
            b.lineComment("register exception table entry");
            b.statement("exHandlerIndex = doCreateExceptionHandler(operationData.tryStartBci, bci, " + UNINIT + " /* handler start */, handlerSp, exceptionIndex)");
            b.end();

            b.startIf().string("operationData.tryReachable").end().startBlock();
            b.startAssert().string("this.reachable").end();
            b.lineComment("emit handler for normal completion case");
            b.statement("doEmitFinallyHandler(operationData.finallyParser, ", finallyHandlerSp, ")");
            // If the finally handler returns/branches, we're not reachable any more.
            // The operationData does not observe this update because it's been popped.
            b.statement("operationData.tryReachable = this.reachable");
            b.startIf().string("this.reachable").end().startBlock();
            b.statement("operationData.endBranchFixupBci = bci + 1");
            buildEmitInstruction(b, model.branchInstruction, new String[]{UNINIT});
            b.end();
            b.end();

            b.startIf().string("operationData.operationReachable").end().startBlock();
            b.lineComment("always emit the exception handler if the operation is reachable");
            b.statement("this.reachable = true");
            b.declaration(type(int.class), "handlerBci", "bci");

            /**
             * The handler can guard more than one bci range if the try block has an early exit
             * (return/branch), because the inlined finally handlers are not guarded. We need to
             * update the handler bci for those exception table entries.
             */
            b.startFor().string("int i = 0; i < operationData.exceptionTableEntryCount; i++").end().startBlock();
            b.declaration(type(int.class), "tableEntryIndex", "operationData.exceptionTableEntries[i]");
            b.statement("exHandlers[tableEntryIndex + 2] = handlerBci /* handler start */");
            b.statement("exHandlers[tableEntryIndex + 3] = handlerSp /* stack height */");
            b.end();
            b.startIf().string("exHandlerIndex != ", UNINIT).end().startBlock();
            b.statement("exHandlers[exHandlerIndex + 2] = handlerBci /* handler start */");
            b.end();

            if (op.kind != OperationKind.FINALLY_TRY_CATCH) {
                b.lineComment("emit handler for exceptional case");
                b.statement("doEmitFinallyHandler(operationData.finallyParser, " + finallyHandlerSp + ")");
                buildEmitInstruction(b, model.throwInstruction, new String[]{"exceptionIndex"});
            }
            b.end();
        }

        /**
         * Produces code to patch the regular finally handler's branch over the exceptional handler.
         */
        private void emitFixFinallyBranchBci(CodeTreeBuilder b) {
            // The regular handler branches over the exceptional handler. Patch its bci.
            b.startIf().string("operationData.endBranchFixupBci != ", UNINIT).end().startBlock();
            b.statement(writeBc("operationData.endBranchFixupBci", "(short) bci"));
            b.end();
        }

        private void buildEmitOperationInstruction(CodeTreeBuilder b, OperationModel operation, List<String> constantOperandIndices) {
            buildEmitOperationInstruction(b, operation, null, "operationSp", constantOperandIndices);
        }

        private void buildEmitOperationInstruction(CodeTreeBuilder b, OperationModel operation, String customChildBci, String sp, List<String> constantOperandIndices) {
            String[] args = switch (operation.kind) {
                case LOAD_LOCAL -> {
                    if (model.enableLocalScoping && model.usesBoxingElimination()) {
                        yield new String[]{
                                        "((BytecodeLocalImpl) " + operation.getOperationBeginArgumentName(0) + ").frameIndex",
                                        "((BytecodeLocalImpl) " + operation.getOperationBeginArgumentName(0) + ").localIndex"};
                    } else {
                        yield new String[]{"((BytecodeLocalImpl) " + operation.getOperationBeginArgumentName(0) + ").frameIndex"};
                    }
                }
                case STORE_LOCAL -> {
                    emitCastCurrentOperationData(b, operation);
                    if (model.usesBoxingElimination()) {
                        if (model.enableLocalScoping) {
                            yield new String[]{
                                            "operationData.local.frameIndex",
                                            "operationData.local.localIndex",
                                            "operationData.childBci"};
                        } else {
                            yield new String[]{"operationData.local.frameIndex",
                                            "operationData.childBci"};
                        }
                    } else {
                        yield new String[]{"operationData.frameIndex"};
                    }
                }
                case STORE_LOCAL_MATERIALIZED -> {
                    emitCastCurrentOperationData(b, operation);
                    if (model.usesBoxingElimination()) {
                        if (model.enableLocalScoping) {
                            yield new String[]{"operationData.local.frameIndex",
                                            "operationData.local.localIndex",
                                            "operationData.local.rootIndex",
                                            "operationData.childBci"};
                        } else {
                            yield new String[]{"operationData.local.frameIndex",
                                            "operationData.local.rootIndex",
                                            "operationData.childBci"};
                        }
                    } else {
                        yield new String[]{"operationData.frameIndex"};
                    }
                }
                case LOAD_LOCAL_MATERIALIZED -> {
                    emitCastCurrentOperationData(b, operation);
                    if (model.usesBoxingElimination()) {
                        if (model.enableLocalScoping) {
                            yield new String[]{
                                            "operationData.frameIndex",
                                            "operationData.localIndex",
                                            "operationData.rootIndex"};
                        } else {
                            yield new String[]{
                                            "operationData.frameIndex",
                                            "operationData.rootIndex"};
                        }
                    } else {
                        yield new String[]{"operationData.frameIndex"};
                    }
                }
                case RETURN -> new String[]{};
                case LOAD_ARGUMENT -> new String[]{operation.getOperationBeginArgumentName(0)};
                case LOAD_CONSTANT -> new String[]{"constantPool.addConstant(" + operation.getOperationBeginArgumentName(0) + ")"};
                case YIELD -> {
                    b.declaration(context.getType(int.class), "constantPoolIndex", "allocateContinuationConstant()");
                    b.startIf().string("reachable").end().startBlock();
                    b.startStatement().startCall("continuationLocations.add");
                    b.startNew(continuationLocation.asType()).string("constantPoolIndex").string("bci + " + operation.instruction.getInstructionLength()).string("currentStackHeight").end();
                    b.end(2); // statement + call
                    b.end(); // if block
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
            emitThrowIllegalStateException(b, "\"BytecodeLabel already emitted. Each label must be emitted exactly once.\"");
            b.end();

            b.startIf().string("labelImpl.declaringOp != operationStack[operationSp - 1].sequenceNumber").end().startBlock();
            emitThrowIllegalStateException(b, "\"BytecodeLabel must be emitted inside the same operation it was created in.\"");
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
            emitThrowIllegalStateException(b, "\"Branch must be targeting a label that is declared in an enclosing operation. Jumps into other operations are not permitted.\"");
            b.end();

            b.startIf().string("labelImpl.isDefined()").end().startBlock();
            emitThrowIllegalStateException(b, "\"Backward branches are unsupported. Use a While operation to model backward control flow.\"");
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

            b.startStatement().startCall("doEmitInstruction");
            b.tree(createInstructionConstant(model.branchInstruction));
            b.string("0"); // stack effect
            b.string(UNINIT); // branch target
            b.end(2);
        }

        private CodeExecutableElement createEmitOperationBegin() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "validateRootOperationBegin");

            CodeTreeBuilder b = ex.createBuilder();

            b.startIf().string("rootOperationSp == -1").end().startBlock(); // {
            b.startThrow().startNew(context.getType(IllegalStateException.class));
            b.string("\"Unexpected operation emit - no root operation present. Did you forget a beginRoot()?\"").end();
            b.end(2);
            b.end(); // }

            return ex;
        }

        private CodeExecutableElement createEmit(OperationModel operation) {
            Modifier visibility = operation.isInternal ? PRIVATE : PUBLIC;
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(visibility), context.getType(void.class), "emit" + operation.name);
            ex.setVarArgs(operation.operationBeginArgumentVarArgs);

            for (OperationArgument arg : operation.operationBeginArguments) {
                ex.addParameter(arg.toVariableElement());
            }
            assert operation.operationEndArguments.length == 0;

            addBeginOrEmitOperationDoc(operation, ex);

            CodeTreeBuilder b = ex.createBuilder();

            if (model.enableSerialization && !operation.isInternal) {
                b.startIf().string("serialization != null").end().startBlock();
                createSerializeBegin(operation, b);
                b.statement("return");
                b.end();
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

            if (operation.requiresRootOperation()) {
                b.startStatement().startCall("validateRootOperationBegin").end(2);
            }

            b.startStatement().startCall("beforeChild").end(2);

            switch (operation.kind) {
                case LOAD_LOCAL:
                    if (model.enableLocalScoping) {
                        createThrowInvalidScope(b, operation);
                    }
                    break;
            }

            if (operation.kind == OperationKind.LABEL) {
                buildEmitLabel(b, operation);
            } else if (operation.kind == OperationKind.BRANCH) {
                buildEmitBranch(b, operation);
            } else {
                assert operation.instruction != null;
                buildEmitOperationInstruction(b, operation, constantOperandIndices);
            }
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
            assert operation.kind != OperationKind.CUSTOM_SHORT_CIRCUIT;

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
                    case LOCAL_OFFSET, LOCAL_INDEX, LOCAL_ROOT, INTEGER, BRANCH_PROFILE, STACK_POINTER -> throw new AssertionError(
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
                b.string(argument.name());
                b.end();
            }
            b.end();
        }

        private enum BeforeChildKind {
            TRANSPARENT,
            SHORT_CIRCUIT,
            UPDATE_REACHABLE,
            DEFAULT,
        }

        private CodeExecutableElement createBeforeChild() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "beforeChild");
            CodeTreeBuilder b = ex.createBuilder();

            b.startIf().string("operationSp == 0").end().startBlock();
            b.statement("return");
            b.end();

            b.statement("int childIndex = operationStack[operationSp - 1].childCount");
            b.startSwitch().string("operationStack[operationSp - 1].operation").end().startBlock();

            Map<BeforeChildKind, List<OperationModel>> groupedOperations = model.getOperations().stream().filter(OperationModel::hasChildren).collect(Collectors.groupingBy(op -> {
                if (op.isTransparent && (op.isVariadic || op.numDynamicOperands() > 1)) {
                    return BeforeChildKind.TRANSPARENT;
                } else if (op.kind == OperationKind.CUSTOM_SHORT_CIRCUIT) {
                    return BeforeChildKind.SHORT_CIRCUIT;
                } else if (op.kind == OperationKind.TRY_CATCH ||
                                op.kind == OperationKind.IF_THEN_ELSE ||
                                op.kind == OperationKind.IF_THEN ||
                                op.kind == OperationKind.CONDITIONAL ||
                                op.kind == OperationKind.FINALLY_TRY ||
                                op.kind == OperationKind.FINALLY_TRY_CATCH) {
                    return BeforeChildKind.UPDATE_REACHABLE;
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

                    emitCastOperationData(b, op, "operationSp - 1");
                    b.startIf().string("childIndex != 0").end().startBlock();
                    if (!op.instruction.shortCircuitModel.returnConvertedBoolean()) {
                        // DUP so the boolean converter doesn't clobber the original value.
                        buildEmitInstruction(b, model.dupInstruction);
                    }

                    b.declaration(type(int.class), "converterBci", "bci");

                    buildEmitBooleanConverterInstruction(b, op.instruction);
                    b.startIf().string("this.reachable").end().startBlock();
                    b.statement("operationData.branchFixupBcis.add(bci + " + op.instruction.getImmediate("branch_target").offset() + ")");
                    b.end();
                    buildEmitInstruction(b, op.instruction, emitShortCircuitArguments(op.instruction));
                    b.end(); // fallthrough

                    b.statement("break");
                    b.end();
                }
            }

            if (groupedOperations.containsKey(BeforeChildKind.UPDATE_REACHABLE)) {
                for (OperationModel op : groupedOperations.get(BeforeChildKind.UPDATE_REACHABLE)) {
                    b.startCase().tree(createOperationConstant(op)).end();
                }
                b.startCaseBlock();

                // catch block is always assumed reachable
                b.startIf().string("childIndex >= 1").end().startBlock();
                b.statement("updateReachable()");
                b.end();

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
            return models.stream().collect(Collectors.groupingBy((m) -> getDataClassName(m))).values();
        }

        private void buildEmitBooleanConverterInstruction(CodeTreeBuilder b, InstructionModel shortCircuitInstruction) {
            InstructionModel booleanConverter = shortCircuitInstruction.shortCircuitModel.booleanConverterInstruction();

            List<InstructionImmediate> immediates = booleanConverter.getImmediates();
            String[] args = new String[immediates.size()];
            for (int i = 0; i < args.length; i++) {
                InstructionImmediate immediate = immediates.get(i);
                args[i] = switch (immediate.kind()) {
                    case BYTECODE_INDEX -> {
                        if (shortCircuitInstruction.shortCircuitModel.returnConvertedBoolean()) {
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
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "afterChild");
            ex.addParameter(new CodeVariableElement(context.getType(boolean.class), "producedValue"));
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "childBci"));
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
                        b.startThrow().startNew(context.getType(IllegalStateException.class));
                        b.string("\"Operation " + op.name + " expected a value-producing child at position \"",
                                        " + childIndex + ",
                                        "\", but a void one was provided. This likely indicates a bug in the parser.\"");
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
                        b.startThrow().startNew(context.getType(IllegalStateException.class));
                        b.string("\"Operation " + op.name + " expected a value-producing child at position \"",
                                        " + childIndex + ",
                                        "\", but a void one was provided. This likely indicates a bug in the parser.\"");
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
                    case IF_THEN:
                        emitCastOperationData(b, op, "operationSp - 1");
                        b.startIf().string("childIndex == 0").end().startBlock();
                        b.startIf().string("reachable").end().startBlock();
                        b.statement("operationData.falseBranchFixupBci = bci + " + model.branchFalseInstruction.getImmediates(ImmediateKind.BYTECODE_INDEX).get(0).offset());
                        b.end();
                        buildEmitInstruction(b, model.branchFalseInstruction, emitBranchArguments(model.branchFalseInstruction));
                        b.end().startElseBlock();
                        b.statement("int toUpdate = operationData.falseBranchFixupBci");
                        b.startIf().string("toUpdate != ", UNINIT).end().startBlock();
                        b.statement(writeBc("toUpdate", "(short) bci"));
                        b.end();
                        b.end();
                        if (model.enableTracing) {
                            b.statement("basicBlockBoundary[bci] = true");
                        }
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
                    case IF_THEN_ELSE:
                        emitCastOperationData(b, op, "operationSp - 1");
                        b.startIf().string("childIndex == 0").end().startBlock();
                        b.startIf().string("reachable").end().startBlock();
                        b.statement("operationData.falseBranchFixupBci = bci + 1");
                        b.end();
                        buildEmitInstruction(b, model.branchFalseInstruction, emitBranchArguments(model.branchFalseInstruction));
                        b.end().startElseIf().string("childIndex == 1").end().startBlock();
                        b.startIf().string("reachable").end().startBlock();
                        b.statement("operationData.endBranchFixupBci = bci + 1");
                        b.end();
                        buildEmitInstruction(b, model.branchInstruction, new String[]{UNINIT});
                        b.statement("int toUpdate = operationData.falseBranchFixupBci");
                        b.startIf().string("toUpdate != ", UNINIT).end().startBlock();
                        b.statement(writeBc("toUpdate", "(short) bci"));
                        b.end();
                        b.end().startElseBlock();
                        b.statement("int toUpdate = operationData.endBranchFixupBci");
                        b.startIf().string("toUpdate != ", UNINIT).end().startBlock();
                        b.statement(writeBc("toUpdate", "(short) bci"));
                        b.end();
                        b.end();
                        if (model.enableTracing) {
                            b.statement("basicBlockBoundary[bci] = true");
                        }
                        break;
                    case CONDITIONAL:
                        emitCastOperationData(b, op, "operationSp - 1");
                        b.startIf().string("childIndex == 0").end().startBlock();
                        if (model.usesBoxingElimination()) {
                            buildEmitInstruction(b, model.dupInstruction);
                        }
                        b.startIf().string("reachable").end().startBlock();
                        b.statement("operationData.falseBranchFixupBci = bci + 1");
                        b.end();
                        buildEmitInstruction(b, model.branchFalseInstruction, emitBranchArguments(model.branchFalseInstruction));

                        b.end().startElseIf().string("childIndex == 1").end().startBlock();
                        if (model.usesBoxingElimination()) {
                            b.statement("operationData.child0Bci = childBci");
                        }
                        b.startIf().string("reachable").end().startBlock();
                        b.statement("operationData.endBranchFixupBci = bci + 1");
                        buildEmitInstruction(b, model.branchInstruction, new String[]{UNINIT});
                        // we have to adjust the stack for the third child
                        b.end();
                        b.statement("currentStackHeight -= 1");

                        b.statement("int toUpdate = operationData.falseBranchFixupBci");
                        b.startIf().string("toUpdate != ", UNINIT).end().startBlock();
                        b.statement(writeBc("toUpdate", "(short) bci"));
                        b.end();
                        b.end().startElseBlock();
                        if (model.usesBoxingElimination()) {
                            b.statement("operationData.child1Bci = childBci");
                        }
                        b.statement("int toUpdate = operationData.endBranchFixupBci");
                        b.startIf().string("toUpdate != ", UNINIT).end().startBlock();
                        b.statement(writeBc("toUpdate", "(short) bci"));
                        b.end();
                        b.end();
                        if (model.enableTracing) {
                            b.statement("basicBlockBoundary[bci] = true");
                        }
                        break;
                    case WHILE:
                        emitCastOperationData(b, op, "operationSp - 1");
                        b.startIf().string("childIndex == 0").end().startBlock();
                        b.startIf().string("reachable").end().startBlock();
                        b.statement("operationData.endBranchFixupBci = bci + 1");
                        b.end();
                        buildEmitInstruction(b, model.branchFalseInstruction, emitBranchArguments(model.branchFalseInstruction));
                        b.end().startElseBlock();
                        buildEmitInstruction(b, model.branchBackwardInstruction, new String[]{"(short) operationData.whileStartBci"});
                        b.statement("int toUpdate = operationData.endBranchFixupBci");
                        b.startIf().string("toUpdate != ", UNINIT).end().startBlock();
                        b.statement(writeBc("toUpdate", "(short) bci"));
                        b.end();
                        b.end();
                        if (model.enableTracing) {
                            b.statement("basicBlockBoundary[bci] = true");
                        }
                        break;
                    case TRY_CATCH:
                        emitCastOperationData(b, op, "operationSp - 1");
                        b.startIf().string("childIndex == 0").end().startBlock();
                        b.startIf().string("operationData.operationReachable").end().startBlock();
                        b.declaration(type(int.class), "tryEndBci", "bci");

                        b.startIf().string("operationData.tryReachable").end().startBlock();
                        b.statement("operationData.endBranchFixupBci = bci + 1");
                        buildEmitInstruction(b, model.branchInstruction, new String[]{UNINIT});
                        b.end(); // if tryReachable

                        /**
                         * The handler can guard more than one bci range if the try block has an
                         * early exit (return/branch), because we need to close the range before
                         * emitting any outer finally handlers. We need to update the handler bci
                         * for those exception table entries.
                         */
                        b.startFor().string("int i = 0; i < operationData.exceptionTableEntryCount; i++").end().startBlock();
                        b.declaration(type(int.class), "tableEntryIndex", "operationData.exceptionTableEntries[i]");
                        b.statement("exHandlers[tableEntryIndex + 2] = bci /* handler start */");
                        b.end();
                        b.statement("doCreateExceptionHandler(operationData.tryStartBci, tryEndBci, bci, operationData.startStackHeight, operationData.exceptionLocalFrameIndex)");

                        b.end(); // if operationReachable
                        b.end();

                        b.startElseIf().string("childIndex == 1").end().startBlock();
                        b.statement("int toUpdate = operationData.endBranchFixupBci");
                        b.startIf().string("toUpdate != ", UNINIT).end().startBlock();
                        b.statement(writeBc("toUpdate", "(short) bci"));
                        b.end();
                        b.end();
                        if (model.enableTracing) {
                            b.statement("basicBlockBoundary[bci] = true");
                        }
                        break;
                    case FINALLY_TRY:
                        break;
                    case FINALLY_TRY_CATCH:
                        emitCastOperationData(b, op, "operationSp - 1");
                        b.startIf().string("childIndex == 0").end().startBlock();
                        emitFinallyHandlersAfterTry(b, op, "operationSp - 1");
                        b.end().startElseBlock();
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
                    case BYTECODE_INDEX -> (index == 0) ? UNINIT : "converterBci";
                    case BRANCH_PROFILE -> "allocateBranchProfile()";
                    default -> throw new AssertionError("Unexpected immediate: " + immediate);
                };
            }
            return branchArguments;
        }

        private String[] emitBranchArguments(InstructionModel instruction) {
            List<InstructionImmediate> immedates = instruction.getImmediates();
            String[] branchArguments = new String[immedates.size()];
            for (int index = 0; index < branchArguments.length; index++) {
                InstructionImmediate immediate = immedates.get(index);
                branchArguments[index] = switch (immediate.kind()) {
                    case BYTECODE_INDEX -> (index == 0) ? UNINIT : "childBci";
                    case BRANCH_PROFILE -> "allocateBranchProfile()";
                    default -> throw new AssertionError("Unexpected immediate: " + immediate);
                };
            }
            return branchArguments;
        }

        private String[] emitMergeConditionalArguments(InstructionModel instr) {
            List<InstructionImmediate> immedates = instr.getImmediates();
            String[] branchArguments = new String[immedates.size()];
            for (int index = 0; index < branchArguments.length; index++) {
                InstructionImmediate immediate = immedates.get(index);
                branchArguments[index] = switch (immediate.kind()) {
                    case BYTECODE_INDEX -> (index == 0) ? "operationData.child0Bci" : "operationData.child1Bci";
                    default -> throw new AssertionError("Unexpected immediate: " + immediate);
                };
            }
            return branchArguments;
        }

        private String[] emitPopArguments(String childBciName) {
            List<InstructionImmediate> immedates = model.popInstruction.getImmediates();
            String[] branchArguments = new String[immedates.size()];
            for (int index = 0; index < branchArguments.length; index++) {
                InstructionImmediate immediate = immedates.get(index);
                branchArguments[index] = switch (immediate.kind()) {
                    case BYTECODE_INDEX -> childBciName;
                    default -> throw new AssertionError("Unexpected immediate: " + immediate);
                };
            }
            return branchArguments;
        }

        private CodeExecutableElement createDoEmitLocal() {
            if (model.enableLocalScoping) {
                bytecodeNodeGen.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "LOCALS_OFFSET_START_BCI")).createInitBuilder().string("0");
                bytecodeNodeGen.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "LOCALS_OFFSET_END_BCI")).createInitBuilder().string("1");
                bytecodeNodeGen.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "LOCALS_OFFSET_INDEX")).createInitBuilder().string("2");
                bytecodeNodeGen.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "LOCALS_OFFSET_NAME")).createInitBuilder().string("3");
                bytecodeNodeGen.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "LOCALS_OFFSET_INFO")).createInitBuilder().string("4");
                bytecodeNodeGen.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "LOCALS_LENGTH")).createInitBuilder().string("5");
            } else {
                bytecodeNodeGen.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "LOCALS_OFFSET_NAME")).createInitBuilder().string("0");
                bytecodeNodeGen.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "LOCALS_OFFSET_INFO")).createInitBuilder().string("1");
                bytecodeNodeGen.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "LOCALS_LENGTH")).createInitBuilder().string("2");
            }

            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(int.class), "doEmitLocal");
            ex.addParameter(new CodeVariableElement(type(Object.class), "name"));
            ex.addParameter(new CodeVariableElement(type(Object.class), "info"));
            ex.addParameter(new CodeVariableElement(type(int.class), "localIndex"));
            if (model.enableLocalScoping) {
                ex.addParameter(new CodeVariableElement(type(int.class), "frameIndex"));
            }
            CodeTreeBuilder b = ex.createBuilder();
            b.declaration(type(int.class), "tableIndex", "localIndex * LOCALS_LENGTH");
            b.statement("ensureLocalsCapacity(tableIndex + LOCALS_LENGTH)");

            if (model.enableLocalScoping) {
                b.statement("assert frameIndex - USER_LOCALS_START_IDX >= 0");

                b.statement("locals[tableIndex + LOCALS_OFFSET_START_BCI] = bci");
                b.lineComment("will be patched later at the end of the block");
                b.statement("locals[tableIndex + LOCALS_OFFSET_END_BCI] = -1");
                b.statement("locals[tableIndex + LOCALS_OFFSET_INDEX] = frameIndex");
            }

            b.declaration(type(int.class), "nameId", "-1");
            b.startIf().string("name != null").end().startBlock();
            b.statement("nameId = constantPool.addConstant(name)");
            b.end();

            b.declaration(type(int.class), "infoId", "-1");
            b.startIf().string("info != null").end().startBlock();
            b.statement("infoId = constantPool.addConstant(info)");
            b.end();

            b.statement("locals[tableIndex + LOCALS_OFFSET_NAME] = nameId");
            b.statement("locals[tableIndex + LOCALS_OFFSET_INFO] = infoId");

            b.statement("return tableIndex");
            return ex;

        }

        private CodeExecutableElement createEnsureLocalsCapacity() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "ensureLocalsCapacity");
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "size"));
            CodeTreeBuilder b = ex.createBuilder();
            b.startIf().string("locals == null").end().startBlock();
            b.startAssign("locals").startNewArray(arrayOf(type(int.class)), CodeTreeBuilder.singleString("LOCALS_LENGTH * 8")).end().end();
            b.end();
            b.startIf().string("size > locals.length").end().startBlock();
            b.startAssign("locals").startStaticCall(context.getType(Arrays.class), "copyOf");
            b.string("locals");
            b.string("Math.max(size, locals.length * 2)");
            b.end(2); // assign, static call
            b.end(); // if block
            return ex;
        }

        private CodeExecutableElement ensureDoEmitInstructionCreated(int argumentLength) {
            return doEmitInstructionMethods.computeIfAbsent(argumentLength, (length) -> createDoEmitInstruction(length));
        }

        private CodeExecutableElement createDoEmitInstruction(int argumentLength) {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(boolean.class), "doEmitInstruction");
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "instruction"));
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "stackEffect"));
            for (int i = 0; i < argumentLength; i++) {
                ex.addParameter(new CodeVariableElement(type(int.class), "data" + i));
            }
            CodeTreeBuilder b = ex.createBuilder();

            b.startIf().string("stackEffect != 0").end().startBlock();
            b.statement("currentStackHeight += stackEffect");
            b.startAssert().string("currentStackHeight >= 0").end();
            b.end();

            b.startIf().string("!reachable").end().startBlock();
            b.statement("return false");
            b.end();

            b.startIf().string("stackEffect > 0").end().startBlock();
            b.statement("maxStackHeight = Math.max(maxStackHeight, currentStackHeight)");
            b.end();

            b.declaration(type(int.class), "newSize", "bci + " + (argumentLength + 1));
            b.startIf().string("newSize > bc.length").end().startBlock();
            b.statement("ensureBytecodeCapacity(newSize)");
            b.end();

            if (model.enableTracing) {
                // since we can mark a start of the BB before it's first instruction is emitted,
                // basicBlockBoundary must always be at least 1 longer than `bc` array to prevent
                // ArrayIndexOutOfBoundsException
                b.startAssign("basicBlockBoundary").startStaticCall(context.getType(Arrays.class), "copyOf");
                b.string("basicBlockBoundary");
                b.string("newSize + 1");
                b.end(2);
            }

            b.end();

            b.statement(writeBc("bci + 0", "(short) instruction"));
            for (int i = 0; i < argumentLength; i++) {
                b.statement(writeBc("bci + " + (i + 1), "(short) data" + i));
            }

            b.statement("bci = bci + " + (argumentLength + 1));
            b.statement("return true");

            return ex;
        }

        private CodeExecutableElement createEnsureBytecodeCapacity() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "ensureBytecodeCapacity");
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "size"));
            CodeTreeBuilder b = ex.createBuilder();
            b.startIf().string("size > bc.length").end().startBlock();
            b.startAssign("bc").startStaticCall(context.getType(Arrays.class), "copyOf");
            b.string("bc");
            b.string("Math.max(size, bc.length * 2)");
            b.end(2); // assign, static call
            b.end(); // if block
            return ex;
        }

        private CodeExecutableElement createDoEmitVariadic() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "doEmitVariadic");
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "count"));
            CodeTreeBuilder b = ex.createBuilder();

            b.statement("currentStackHeight -= count - 1");
            b.startIf().string("!reachable").end().startBlock();
            b.statement("return");
            b.end();

            int variadicCount = model.popVariadicInstruction.length - 1;

            b.startIf().string("count <= ").string(variadicCount).end().startBlock();
            b.startStatement().string("doEmitInstruction(").tree(createInstructionConstant(model.popVariadicInstruction[0])).string(" + count, 0)").end();
            b.end().startElseBlock();

            b.startIf().string("currentStackHeight + count > maxStackHeight").end().startBlock();
            b.statement("maxStackHeight = currentStackHeight + count");
            b.end();
            b.statement("int elementCount = count + 1");
            b.startStatement().startCall("doEmitInstruction").tree(createInstructionConstant(model.storeNullInstruction)).string("0").end(2);

            b.startWhile().string("elementCount > 8").end().startBlock();
            b.startStatement().startCall("doEmitInstruction").tree(createInstructionConstant(model.popVariadicInstruction[variadicCount])).string("0").end(2);
            b.statement("elementCount -= 7");
            b.end();

            b.startIf().string("elementCount > 0").end().startBlock();
            b.startStatement().startCall("doEmitInstruction").startGroup().tree(createInstructionConstant(model.popVariadicInstruction[0])).string(" + elementCount").end().string("0").end(2);
            b.end();
            b.startStatement().startCall("doEmitInstruction").tree(createInstructionConstant(model.mergeVariadicInstruction)).string("0").end(2);
            b.end();

            b.startIf().string("count == 0 && currentStackHeight > maxStackHeight").end().startBlock();
            b.statement("maxStackHeight = currentStackHeight");
            b.end();

            return ex;
        }

        private void buildEmitInstruction(CodeTreeBuilder b, InstructionModel instr, String... arguments) {
            int stackEffect = 0;
            switch (instr.kind) {
                case BRANCH:
                case BRANCH_BACKWARD:
                case TAG_ENTER:
                case TAG_LEAVE:
                case TAG_LEAVE_VOID:
                case TAG_RESUME:
                case TAG_YIELD:
                case LOAD_LOCAL_MATERIALIZED:
                case THROW:
                case YIELD:
                case CLEAR_LOCAL:
                    break;
                case RETURN:
                case BRANCH_FALSE:
                case POP:
                case STORE_LOCAL:
                case MERGE_CONDITIONAL:
                    stackEffect = -1;
                    break;
                case CUSTOM:
                    int delta = (instr.signature.isVoid ? 0 : 1) - instr.signature.dynamicOperandCount;
                    stackEffect = delta;
                    break;
                case CUSTOM_SHORT_CIRCUIT:
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
                    if (shortCircuitInstruction.returnConvertedBoolean()) {
                        // Stack: [..., convertedValue]
                        stackEffect = -1;
                    } else {
                        // Stack: [..., value, convertedValue]
                        stackEffect = -2;
                    }
                    break;
                case DUP:
                case LOAD_ARGUMENT:
                case LOAD_CONSTANT:
                case LOAD_LOCAL:
                    stackEffect = 1;
                    break;
                case STORE_LOCAL_MATERIALIZED:
                    stackEffect = -2;
                    break;
                case STORE_NULL:
                    stackEffect = 1;
                    break;
                default:
                    throw new UnsupportedOperationException();
            }

            b.startStatement().startCall("doEmitInstruction");
            b.tree(createInstructionConstant(instr));
            b.string(stackEffect);
            int argumentsLength = arguments != null ? arguments.length : 0;
            if (argumentsLength != instr.immediates.size()) {
                throw new AssertionError("Invalid number of immediates for instruction " + instr.name + ". Expected " + instr.immediates.size() + " but got " + argumentsLength + ". Immediates" +
                                instr.getImmediates());
            }

            if (arguments != null) {
                ensureDoEmitInstructionCreated(arguments.length);
                for (String argument : arguments) {
                    b.string(argument);
                }
            } else {
                ensureDoEmitInstructionCreated(0);
            }
            b.end(2);
        }

        private CodeExecutableElement createDoEmitSourceInfo() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "doEmitSourceInfo");
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "sourceIndex"));
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "beginBci"));
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "endBci"));
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "start"));
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "length"));

            CodeTreeBuilder b = ex.createBuilder();

            b.startAssert().string("parseSources").end();

            b.startIf().string("rootOperationSp == -1").end().startBlock();
            b.returnStatement();
            b.end();

            b.declaration(type(int.class), "encodedBcis", "(beginBci << 16) | endBci");
            b.declaration(type(int.class), "index", "sourceInfoIndex ");
            b.declaration(type(int.class), "prevIndex", "index - SOURCE_INFO_LENGTH ");

            b.startIf();
            b.string("prevIndex >= 0").newLine().startIndention();
            b.string(" && (sourceInfo[prevIndex + SOURCE_INFO_OFFSET_BCI]) == encodedBcis").newLine();
            b.string(" && (sourceInfo[prevIndex + SOURCE_INFO_OFFSET_SOURCE]) == sourceIndex").newLine();
            b.string(" && (sourceInfo[prevIndex + SOURCE_INFO_OFFSET_START]) == start").newLine();
            b.string(" && (sourceInfo[prevIndex + SOURCE_INFO_OFFSET_LENGTH]) == length");
            b.end();
            b.end().startBlock();
            b.lineComment("duplicate entry");
            b.statement("return");
            b.end();

            b.startIf().string("index >= sourceInfo.length").end().startBlock();
            b.statement("sourceInfo = Arrays.copyOf(sourceInfo, sourceInfo.length * 2)");
            b.end();

            b.statement("sourceInfo[index + SOURCE_INFO_OFFSET_BCI] = encodedBcis");
            b.statement("sourceInfo[index + SOURCE_INFO_OFFSET_SOURCE] = sourceIndex");
            b.statement("sourceInfo[index + SOURCE_INFO_OFFSET_START] = start");
            b.statement("sourceInfo[index + SOURCE_INFO_OFFSET_LENGTH] = length");

            b.statement("sourceInfoIndex = index + SOURCE_INFO_LENGTH");

            bytecodeNodeGen.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "SOURCE_INFO_OFFSET_BCI")).createInitBuilder().string("0");
            bytecodeNodeGen.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "SOURCE_INFO_OFFSET_SOURCE")).createInitBuilder().string("1");
            bytecodeNodeGen.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "SOURCE_INFO_OFFSET_START")).createInitBuilder().string("2");
            bytecodeNodeGen.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "SOURCE_INFO_OFFSET_LENGTH")).createInitBuilder().string("3");
            bytecodeNodeGen.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "SOURCE_INFO_LENGTH")).createInitBuilder().string("4");

            return ex;
        }

        private CodeExecutableElement createDoEmitFinallyHandler() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "doEmitFinallyHandler");
            ex.addParameter(new CodeVariableElement(declaredType(Runnable.class), "finallyParser"));
            ex.addParameter(new CodeVariableElement(type(int.class), "finallyOperationSp"));

            CodeTreeBuilder b = ex.createBuilder();

            buildBegin(b, model.finallyHandlerOperation, "finallyOperationSp");
            b.statement("finallyParser.run()");
            buildEnd(b, model.finallyHandlerOperation);

            return ex;
        }

        private CodeExecutableElement createDoCreateExceptionHandler() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(int.class), "doCreateExceptionHandler");
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "startBci"));
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "endBci"));
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "handlerBci"));
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "spStart"));
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "exceptionLocal"));

            CodeTreeBuilder b = ex.createBuilder();

            b.startAssert().string("startBci <= endBci").end();
            // Don't create empty handler ranges.
            b.startIf().string("startBci == endBci").end().startBlock();
            b.startReturn().string(UNINIT).end();
            b.end();
            /**
             * Special case: if the range only has a single return/branch instruction, don't emit
             * it. This case happens when a try-finally's try block ends in a return/branch
             * instruction. We close the exception range, emit the finally handler, then reopen a
             * new exception range that only guards the return/branch. This instruction will never
             * throw, so the table entry is unnecessary.
             */
            b.startElseIf();
            b.string("endBci - startBci == ", String.valueOf(model.returnInstruction.getInstructionLength()));
            b.string(" && bc[startBci] == ").tree(createInstructionConstant(model.returnInstruction));
            b.end().startBlock();
            b.startReturn().string(UNINIT).end();
            b.end().startElseIf();
            b.string("endBci - startBci == ", String.valueOf(model.branchInstruction.getInstructionLength()));
            b.string(" && bc[startBci] == ").tree(createInstructionConstant(model.branchInstruction));
            b.end().startBlock();
            b.startReturn().string(UNINIT).end();
            b.end();

            b.startIf().string("exHandlers.length <= exHandlerCount + EXCEPTION_HANDLER_LENGTH").end().startBlock();
            b.statement("exHandlers = Arrays.copyOf(exHandlers, exHandlers.length * 2)");
            b.end();

            b.statement("int result = exHandlerCount");
            b.statement("exHandlers[exHandlerCount + EXCEPTION_HANDLER_OFFSET_START_BCI] = startBci");
            b.statement("exHandlers[exHandlerCount + EXCEPTION_HANDLER_OFFSET_END_BCI] = endBci");
            b.statement("exHandlers[exHandlerCount + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI] = handlerBci");
            b.statement("exHandlers[exHandlerCount + EXCEPTION_HANDLER_OFFSET_STACK_POINTER] = spStart");
            b.statement("exHandlers[exHandlerCount + EXCEPTION_HANDLER_OFFSET_LOCAL] = exceptionLocal");
            b.statement("exHandlerCount += EXCEPTION_HANDLER_LENGTH");

            b.statement("return result");

            bytecodeNodeGen.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "EXCEPTION_HANDLER_OFFSET_START_BCI")).createInitBuilder().string("0");
            bytecodeNodeGen.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "EXCEPTION_HANDLER_OFFSET_END_BCI")).createInitBuilder().string("1");
            bytecodeNodeGen.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "EXCEPTION_HANDLER_OFFSET_HANDLER_BCI")).createInitBuilder().string("2");
            bytecodeNodeGen.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "EXCEPTION_HANDLER_OFFSET_STACK_POINTER")).createInitBuilder().string("3");
            bytecodeNodeGen.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "EXCEPTION_HANDLER_OFFSET_LOCAL")).createInitBuilder().string("4");
            bytecodeNodeGen.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "EXCEPTION_HANDLER_LENGTH")).createInitBuilder().string("5");

            return ex;
        }

        private CodeExecutableElement createToString() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(declaredType(Object.class), "toString");
            CodeTreeBuilder b = ex.createBuilder();

            b.startDeclaration(type(StringBuilder.class), "b").startNew(type(StringBuilder.class)).end().end();
            b.startStatement().startCall("b.append").startGroup().typeLiteral(bytecodeNodeGen.asType()).string(".getSimpleName()").end().end().end();
            b.statement("b.append('.')");
            b.startStatement().startCall("b.append").startGroup().typeLiteral(builder.asType()).string(".getSimpleName()").end().end().end();
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

            b.startStatement().startCall("b.append").doubleQuote("\\n     bytecodeIndex = ").end().startCall(".append").string("bci").end().end();
            b.startStatement().startCall("b.append").doubleQuote("\\n     stackPointer = ").end().startCall(".append").string("currentStackHeight").end().end();
            b.startStatement().startCall("b.append").doubleQuote("\\n     bytecodes = ").end().startCall(".append").string("parseBytecodes").end().end();
            b.startStatement().startCall("b.append").doubleQuote("\\n     sources = ").end().startCall(".append").string("parseSources").end().end();

            if (!model.instrumentations.isEmpty()) {
                b.startStatement().startCall("b.append").doubleQuote("\\n    instruments = [").end().end();
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
                b.startStatement().startCall("b.append").doubleQuote("\\n     tags = ").end().end();
                b.declaration(type(String.class), "sepTag", "\"\"");
                for (TypeMirror tag : model.getProvidedTags()) {
                    b.startIf().string("(tags & CLASS_TO_TAG_MASK.get(").typeLiteral(tag).string(")) != 0").end().startBlock();
                    b.startStatement().startCall("b.append").string("sepTag").end().end();
                    b.startStatement().startCall("b.append").startStaticCall(types.Tag, "getIdentifier").typeLiteral(tag).end().end().end();
                    b.startAssign("sepTag").doubleQuote(",").end();
                    b.end();
                }
            }

            b.startStatement().startCall("b.append").doubleQuote("\\n     stack = ").end().end();

            // for operation stacks
            b.startFor().string("int i = 0; i < operationSp; i++").end().startBlock();
            b.startStatement().startCall("b.append").doubleQuote("\\n       ").end().end();
            b.startFor().string("int y = 0; y < i; y++").end().startBlock();
            b.statement("b.append(\"  \")");
            b.end(); // for
            b.startStatement().startCall("b.append").string("operationStack[i].toString(this)").end().end();
            b.end(); // for

            b.statement("return b.toString()");
            return ex;
        }

        private CodeExecutableElement createDoEmitRoot() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "doEmitRoot");
            CodeTreeBuilder b = ex.createBuilder();

            b.startIf().string("!parseSources").end().startBlock();
            b.lineComment("Nothing to do here without sources");
            b.statement("return");
            b.end();

            buildOperationStackWalk(b, () -> {
                b.startSwitch().string("operationStack[i].operation").end().startBlock();

                b.startCase().tree(createOperationConstant(model.sourceSectionOperation)).end();
                b.startCaseBlock();
                emitCastOperationData(b, model.sourceSectionOperation, "i");
                /**
                 * Any source section on the stack encloses the root. The entire root node's
                 * bytecode range should map to the source section.
                 */
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
         * Before emitting a branch, we may need to emit additional instructions to "resolve"
         * pending operations. In particular, we emit finally handlers and tag leave instructions.
         * If any instructions are emitted, we also need to close and reopen exception handlers.
         */
        private CodeExecutableElement createBeforeEmitBranch() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "beforeEmitBranch");
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "declaringOperationSp"));
            emitExitInstructionsBeforeEarlyExit(ex, OperationKind.BRANCH, "branch", "declaringOperationSp");
            return ex;
        }

        /**
         * Before emitting a return, we may need to emit additional instructions to "resolve"
         * pending operations. In particular, we emit finally handlers, tag leave, and epilog
         * instructions. If any instructions are emitted, we also need to close and reopen exception
         * handlers.
         */
        private CodeExecutableElement createBeforeEmitReturn() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "beforeEmitReturn");
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "parentBci"));
            emitExitInstructionsBeforeEarlyExit(ex, OperationKind.RETURN, "return", "0");
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

            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "doEmitTagYield");

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

            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "doEmitTagResume");
            CodeTreeBuilder b = ex.createBuilder();

            b.startIf().string("tags == 0").end().startBlock();
            b.returnDefault();
            b.end();

            b.startFor().string("int i = 0; i <  operationSp; i++").end().startBlock();
            b.startSwitch().string("operationStack[i].operation").end().startBlock();
            OperationModel op = model.findOperation(OperationKind.TAG);
            b.startCase().tree(createOperationConstant(op)).end();
            b.startBlock();
            emitCastOperationData(b, op, "i");
            buildEmitInstruction(b, model.tagResumeInstruction, "operationData.nodeId");
            b.statement("break");
            b.end(); // case tag

            b.end(); // switch
            b.end(); // for

            return ex;
        }

        /**
         * Generates code to walk the operation stack and emit any "exit" instructions before a
         * branch/return.
         */
        private void emitExitInstructionsBeforeEarlyExit(CodeExecutableElement ex, OperationKind operationKind, String friendlyInstructionName, String lowestOperationIndex) {
            addJavadoc(ex, "Walks the operation stack, emitting instructions for any operations that need to complete before the " + friendlyInstructionName + ".");
            CodeTreeBuilder b = ex.createBuilder();

            emitExitInstructionsStackWalk(b, operationKind, lowestOperationIndex);
            emitReopenHandlersStackWalk(b, lowestOperationIndex);
        }

        /**
         * Generates code to walk the operation stack and emit exit instructions. Also closes
         * exception ranges for exception handlers where necessary.
         */
        private void emitExitInstructionsStackWalk(CodeTreeBuilder b, OperationKind operationKind, String lowestOperationIndex) {
            b.startJavadoc();
            b.string("Emit \"exit\" instructions for any pending operations, closing exception ranges where necessary.").newLine();
            b.end();
            if (operationKind == OperationKind.RETURN) {
                // Remember the bytecode index for boxing elimination.
                b.declaration(type(int.class), "childBci", "parentBci");
            }

            b.declaration(type(boolean.class), "handlerClosed", "false");
            buildOperationStackWalk(b, lowestOperationIndex, () -> {
                b.startSwitch().string("operationStack[i].operation").end().startBlock();

                if (model.enableTagInstrumentation) {
                    b.startCase().tree(createOperationConstant(model.tagOperation)).end();
                    b.startBlock();
                    emitCastOperationData(b, model.tagOperation, "i");
                    if (operationKind == OperationKind.RETURN) {
                        buildEmitInstruction(b, model.tagLeaveValueInstruction, buildTagLeaveArguments(model.tagLeaveValueInstruction));
                        b.statement("childBci = bci - " + model.tagLeaveValueInstruction.getInstructionLength());
                    } else {
                        assert operationKind == OperationKind.BRANCH;
                        buildEmitInstruction(b, model.tagLeaveVoidInstruction, "operationData.nodeId");
                    }
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

                b.startCase().tree(createOperationConstant(model.findOperation(OperationKind.FINALLY_TRY))).end();
                b.startCase().tree(createOperationConstant(model.findOperation(OperationKind.FINALLY_TRY_CATCH))).end();
                b.startBlock();
                emitCastOperationData(b, model.finallyTryOperation, "i");
                b.startIf().string("operationStack[i].childCount == 0 /* still in try */ && reachable").end().startBlock();
                b.startStatement().startCall("operationData.addExceptionTableEntry");
                b.string("doCreateExceptionHandler(operationData.tryStartBci, bci, ", UNINIT, " /* handler start */, ", UNINIT,
                                " /* stack height */, operationData.exceptionLocalFrameIndex)");
                b.end(2);
                b.statement("handlerClosed = true");
                b.statement("doEmitFinallyHandler(operationData.finallyParser, i)");
                b.end();
                b.statement("break");
                b.end(); // case finally

                b.startCase().tree(createOperationConstant(model.findOperation(OperationKind.TRY_CATCH))).end();
                b.startBlock();
                emitCastOperationData(b, model.tryCatchOperation, "i");
                b.startIf().string("operationStack[i].childCount == 0 /* still in try */ && reachable");
                b.end().startBlock();
                b.startStatement().startCall("operationData.addExceptionTableEntry");
                b.string("doCreateExceptionHandler(operationData.tryStartBci, bci, ", UNINIT, " /* handler start */, operationData.startStackHeight, operationData.exceptionLocalFrameIndex)");
                b.end(2);
                b.statement("handlerClosed = true");
                b.end();
                b.statement("break");
                b.end(); // case trycatch

                b.end(); // switch
            });
        }

        /**
         * Generates code to reopen handler ranges after "exiting" the parent operations.
         */
        private void emitReopenHandlersStackWalk(CodeTreeBuilder b, String lowestOperationIndex) {
            b.startJavadoc();
            b.string("Now that all \"exit\" instructions have been emitted, reopen exception handlers.").newLine();
            b.end();
            b.startIf().string("handlerClosed").end().startBlock();

            buildOperationStackWalk(b, lowestOperationIndex, () -> {
                b.startSwitch().string("operationStack[i].operation").end().startBlock();

                b.startCase().tree(createOperationConstant(model.findOperation(OperationKind.FINALLY_TRY))).end();
                b.startCase().tree(createOperationConstant(model.findOperation(OperationKind.FINALLY_TRY_CATCH))).end();
                b.startCaseBlock();
                b.startIf().string("operationStack[i].childCount == 0 /* still in try */").end().startBlock();
                emitCastOperationData(b, model.finallyTryOperation, "i");
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
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(int.class), "allocateNode");
            CodeTreeBuilder b = ex.createBuilder();
            b.startIf().string("!reachable").end().startBlock();
            b.statement("return -1");
            b.end();

            b.startReturn();
            b.string("numNodes++");
            b.end();

            return ex;
        }

        private CodeExecutableElement createAllocateBranchProfile() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(int.class), "allocateBranchProfile");
            CodeTreeBuilder b = ex.createBuilder();

            b.startIf().string("!reachable").end().startBlock();
            b.statement("return -1");
            b.end();

            b.startReturn();
            b.string("numConditionalBranches++");
            b.end();

            return ex;
        }

        private CodeExecutableElement createAllocateContinuationConstant() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(int.class), "allocateContinuationConstant");
            CodeTreeBuilder b = ex.createBuilder();

            b.startIf().string("!reachable").end().startBlock();
            b.statement("return -1");
            b.end();

            b.startReturn();
            b.string("constantPool.allocateSlot()");
            b.end();

            return ex;
        }

        private static String writeBc(String index, String value) {
            return String.format("ACCESS.shortArrayWrite(bc, %s, %s)", index, value);
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
            ctor.addParameter(new CodeVariableElement(bytecodeRootNodesImpl.asType(), "nodes"));
            ctor.addParameter(new CodeVariableElement(types.BytecodeConfig, "config"));

            CodeTreeBuilder javadoc = ctor.createDocBuilder();
            javadoc.startJavadoc();
            javadoc.string("Constructor for initial parses.");
            javadoc.newLine();
            javadoc.end();

            CodeTreeBuilder b = ctor.createBuilder();

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
    }

    class BytecodeRootNodesImplFactory {

        CodeTypeElement updateReason;

        private CodeTypeElement create() {
            bytecodeRootNodesImpl.setSuperClass(generic(types.BytecodeRootNodes, model.templateType.asType()));
            bytecodeRootNodesImpl.setEnclosingElement(bytecodeNodeGen);
            bytecodeRootNodesImpl.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(Object.class), "VISIBLE_TOKEN")).createInitBuilder().string("TOKEN");
            bytecodeRootNodesImpl.add(new CodeVariableElement(Set.of(PRIVATE, VOLATILE), type(long.class), "encoding"));

            updateReason = bytecodeRootNodesImpl.add(createUpdateReason());
            bytecodeRootNodesImpl.add(createConstructor());
            bytecodeRootNodesImpl.add(createReparseImpl());
            bytecodeRootNodesImpl.add(createPerformUpdate());
            bytecodeRootNodesImpl.add(createSetNodes());
            bytecodeRootNodesImpl.add(createGetParserImpl());
            bytecodeRootNodesImpl.add(createValidate());

            if (model.enableSerialization) {
                bytecodeRootNodesImpl.add(createSerialize());
            }

            return bytecodeRootNodesImpl;
        }

        private CodeTypeElement createUpdateReason() {
            DeclaredType charSequence = (DeclaredType) type(CharSequence.class);
            CodeTypeElement reason = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "UpdateReason");
            reason.getImplements().add(charSequence);

            reason.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(boolean.class), "newSources"));
            reason.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(int.class), "newInstrumentations"));
            reason.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(int.class), "newTags"));

            reason.add(GeneratorUtils.createConstructorUsingFields(Set.of(), reason));

            CodeExecutableElement length = reason.add(GeneratorUtils.overrideImplement(charSequence, "length"));
            length.createBuilder().startReturn().string("toString().length()").end();

            CodeExecutableElement charAt = reason.add(GeneratorUtils.overrideImplement(charSequence, "charAt"));
            charAt.renameArguments("index");
            charAt.createBuilder().startReturn().string("toString().charAt(index)").end();

            CodeExecutableElement subSequence = reason.add(GeneratorUtils.overrideImplement(charSequence, "subSequence"));
            subSequence.renameArguments("start", "end");
            subSequence.createBuilder().startReturn().string("toString().subSequence(start, end)").end();

            CodeExecutableElement toString = reason.add(GeneratorUtils.overrideImplement(charSequence, "toString"));
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
            b.statement("super(generator)");
            b.startAssign("this.encoding");
            b.startStaticCall(configEncoder.asType(), "decode").string("config").end();
            b.end();

            return ctor;
        }

        private CodeExecutableElement createReparseImpl() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeRootNodes, "updateImpl");
            addOverride(ex);
            mergeSuppressWarnings(ex, "hiding");
            ex.renameArguments("encoder", "encoding");
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
            b.startGroup().cast(bytecodeNodeGen.asType()).string("node").end();
            b.end(2);
            b.end(2);

            b.startStatement().startCall("parser", "parse").string("builder").end(2);
            b.startStatement().startCall("builder", "finish").end(2);

            b.statement("this.encoding = newEncoding");
            b.statement("return true");

            return ex;
        }

        private CodeExecutableElement createSetNodes() {
            return GeneratorUtils.createSetter(Set.of(), new CodeVariableElement(arrayOf(bytecodeNodeGen.asType()), "nodes"));
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
            b.startStatement().string("(").cast(bytecodeNodeGen.asType(), "node").string(")").string(".getBytecodeNodeImpl().validateBytecodes()").end();
            b.end();

            b.statement("return true");
            return ex;
        }

        private CodeExecutableElement createSerialize() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeRootNodes, "serialize");
            ex.renameArguments("buffer", "callback");
            addOverride(ex);
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
            b.startGroup().cast(bytecodeNodeGen.asType()).string("nodes[i]").end();
            b.end(2);
            b.end();

            b.startStatement();
            b.startStaticCall(bytecodeNodeGen.asType(), "doSerialize");
            b.string("buffer");
            b.string("callback");

            // Create a new Builder with this BytecodeRootNodes instance.
            b.startNew(bytecodeBuilderType);
            b.string("this");
            b.staticReference(types.BytecodeConfig, "COMPLETE");
            b.end();

            b.string("existingNodes");

            b.end(2);

            return ex;
        }
    }

// Generates an Instructions class with constants for each instruction.
    class InstructionConstantsFactory {
        private CodeTypeElement create() {
            for (InstructionModel instruction : BytecodeDSLNodeFactory.this.model.getInstructions()) {
                CodeVariableElement fld = new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), context.getType(short.class), instruction.getConstantName());
                fld.createInitBuilder().string(instruction.getId()).end();
                fld.createDocBuilder().startDoc().lines(instruction.pp()).end(2);
                instructionsElement.add(fld);
            }
            return instructionsElement;
        }
    }

// Generates an Operations class with constants for each operation.
    class OperationsConstantsFactory {
        private CodeTypeElement create() {
            for (OperationModel operation : BytecodeDSLNodeFactory.this.model.getOperations()) {
                CodeVariableElement fld = new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), context.getType(int.class), operation.getConstantName());
                fld.createInitBuilder().string(operation.id).end();
                operationsElement.add(fld);
            }
            return operationsElement;
        }
    }

    class ExceptionHandlerImplFactory {

        private CodeTypeElement type;

        ExceptionHandlerImplFactory() {
        }

        private CodeTypeElement create() {
            type = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL),
                            ElementKind.CLASS, null, "ExceptionHandlerImpl");
            type.setSuperClass(types.ExceptionHandler);

            type.add(new CodeVariableElement(Set.of(FINAL), abstractBytecodeNode.asType(), "bytecode"));
            type.add(new CodeVariableElement(Set.of(FINAL), type(int.class), "baseIndex"));

            CodeExecutableElement constructor = type.add(createConstructorUsingFields(Set.of(), type, null));
            CodeTree tree = constructor.getBodyTree();
            CodeTreeBuilder b = constructor.createBuilder();
            b.startStatement().startSuperCall().staticReference(bytecodeRootNodesImpl.asType(), "VISIBLE_TOKEN").end().end();
            b.tree(tree);
            type.add(createGetKind());
            type.add(createGetStartIndex());
            type.add(createGetEndIndex());
            type.add(createGetHandlerIndex());
            type.add(createGetExceptionVariableIndex());
            type.add(createGetTagTree());
            return type;
        }

        private CodeExecutableElement createGetKind() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.ExceptionHandler, "getKind");
            CodeTreeBuilder b = ex.createBuilder();
            if (hasSpecialHandlers()) {
                b.startSwitch();
                b.string("bytecode.handlers[baseIndex + EXCEPTION_HANDLER_OFFSET_LOCAL]");
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

        private CodeExecutableElement createGetStartIndex() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.ExceptionHandler, "getStartIndex");
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return bytecode.handlers[baseIndex + EXCEPTION_HANDLER_OFFSET_START_BCI]");
            return ex;
        }

        private CodeExecutableElement createGetEndIndex() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.ExceptionHandler, "getEndIndex");
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return bytecode.handlers[baseIndex + EXCEPTION_HANDLER_OFFSET_END_BCI]");
            return ex;
        }

        private CodeExecutableElement createGetHandlerIndex() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.ExceptionHandler, "getHandlerIndex");
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
                b.statement("return super.getHandlerIndex()");
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

        private CodeExecutableElement createGetExceptionVariableIndex() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.ExceptionHandler, "getExceptionVariableIndex");
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
                b.statement("return super.getExceptionVariableIndex()");
                b.end();
                b.caseDefault().startCaseBlock();
                b.statement("return bytecode.handlers[baseIndex + EXCEPTION_HANDLER_OFFSET_LOCAL]");
                b.end();
                b.end(); // switch block
            } else {
                b.statement("return bytecode.handlers[baseIndex + EXCEPTION_HANDLER_OFFSET_LOCAL]");
            }

            return ex;
        }

        private CodeExecutableElement createGetTagTree() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.ExceptionHandler, "getTagTree");
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

    class ExceptionHandlerListFactory {

        private CodeTypeElement type;

        private CodeTypeElement create() {
            type = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL),
                            ElementKind.CLASS, null, "ExceptionHandlerList");
            type.setSuperClass(generic(type(AbstractList.class), types.ExceptionHandler));
            type.add(new CodeVariableElement(Set.of(FINAL), abstractBytecodeNode.asType(), "bytecode"));
            type.add(createConstructorUsingFields(Set.of(), type, null));
            type.add(createGet());
            type.add(createSize());
            return type;
        }

        private CodeExecutableElement createGet() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(declaredType(List.class), "get", 1);
            ex.setReturnType(types.ExceptionHandler);
            ex.renameArguments("index");
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
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(declaredType(List.class), "size");
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return bytecode.handlers.length / EXCEPTION_HANDLER_LENGTH");
            return ex;
        }

    }

    class SourceInformationImplFactory {

        private CodeTypeElement type;

        private CodeTypeElement create() {
            type = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL),
                            ElementKind.CLASS, null, "SourceInformationImpl");
            type.setSuperClass(types.SourceInformation);

            type.add(new CodeVariableElement(Set.of(FINAL), abstractBytecodeNode.asType(), "bytecode"));
            type.add(new CodeVariableElement(Set.of(FINAL), type(int.class), "baseIndex"));

            CodeExecutableElement constructor = type.add(createConstructorUsingFields(Set.of(), type, null));
            CodeTree tree = constructor.getBodyTree();
            CodeTreeBuilder b = constructor.createBuilder();
            b.startStatement().startSuperCall().staticReference(bytecodeRootNodesImpl.asType(), "VISIBLE_TOKEN").end().end();
            b.tree(tree);
            type.add(createGetStartIndex());
            type.add(createGetEndIndex());
            type.add(createGetSourceSection());

            return type;
        }

        private CodeExecutableElement createGetStartIndex() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.SourceInformation, "getStartIndex");
            CodeTreeBuilder b = ex.createBuilder();
            b.declaration(type(int.class), "encodedBci", "bytecode.sourceInfo[baseIndex + SOURCE_INFO_OFFSET_BCI]");
            b.declaration(type(int.class), "beginBci", "(encodedBci >> 16) & 0xFFFF");
            b.statement("return beginBci");
            return ex;
        }

        private CodeExecutableElement createGetEndIndex() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.SourceInformation, "getEndIndex");
            CodeTreeBuilder b = ex.createBuilder();
            b.declaration(type(int.class), "encodedBci", "bytecode.sourceInfo[baseIndex + SOURCE_INFO_OFFSET_BCI]");
            b.declaration(type(int.class), "endBci", "encodedBci & 0xFFFF");
            b.statement("return endBci");
            return ex;
        }

        private CodeExecutableElement createGetSourceSection() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.SourceInformation, "getSourceSection");
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return AbstractBytecodeNode.createSourceSection(bytecode.sources, bytecode.sourceInfo, baseIndex)");
            return ex;
        }

    }

    class SourceInformationListFactory {

        private CodeTypeElement type;

        private CodeTypeElement create() {
            type = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL),
                            ElementKind.CLASS, null, "SourceInformationList");
            type.setSuperClass(generic(type(AbstractList.class), types.SourceInformation));
            type.add(new CodeVariableElement(Set.of(FINAL), abstractBytecodeNode.asType(), "bytecode"));
            type.add(createConstructorUsingFields(Set.of(), type, null));
            type.add(createGet());
            type.add(createSize());
            return type;
        }

        private CodeExecutableElement createGet() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(declaredType(List.class), "get", 1);
            ex.setReturnType(types.SourceInformation);
            ex.renameArguments("index");
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
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(declaredType(List.class), "size");
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return bytecode.sourceInfo.length / SOURCE_INFO_LENGTH");
            return ex;
        }

    }

    class SourceInformationTreeImplFactory {

        private CodeTypeElement type;

        private CodeTypeElement create() {
            type = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL),
                            ElementKind.CLASS, null, "SourceInformationTreeImpl");
            type.setSuperClass(types.SourceInformationTree);

            type.add(new CodeVariableElement(Set.of(FINAL), abstractBytecodeNode.asType(), "bytecode"));
            type.add(new CodeVariableElement(Set.of(FINAL), type(int.class), "baseIndex"));
            type.add(new CodeVariableElement(Set.of(FINAL), generic(List.class, types.SourceInformationTree), "children"));

            CodeExecutableElement constructor = type.add(createConstructorUsingFields(Set.of(), type, null, Set.of("children")));
            CodeTree tree = constructor.getBodyTree();
            CodeTreeBuilder b = constructor.createBuilder();
            b.startStatement().startSuperCall().staticReference(bytecodeRootNodesImpl.asType(), "VISIBLE_TOKEN").end().end();
            b.tree(tree);
            // We prepend items during parsing. Use a linked list for constant time prepends.
            b.startAssign("this.children").startNew(generic(type(LinkedList.class), types.SourceInformationTree)).end(2);

            type.add(createGetStartIndex());
            type.add(createGetEndIndex());
            type.add(createGetSourceSection());
            type.add(createGetChildren());
            type.add(createContains());
            type.add(createParse());

            return type;
        }

        private CodeExecutableElement createGetStartIndex() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.SourceInformation, "getStartIndex");
            CodeTreeBuilder b = ex.createBuilder();
            b.declaration(type(int.class), "encodedBci", "bytecode.sourceInfo[baseIndex + SOURCE_INFO_OFFSET_BCI]");
            b.declaration(type(int.class), "beginBci", "(encodedBci >> 16) & 0xFFFF");
            b.statement("return beginBci");
            return ex;
        }

        private CodeExecutableElement createGetEndIndex() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.SourceInformation, "getEndIndex");
            CodeTreeBuilder b = ex.createBuilder();
            b.declaration(type(int.class), "encodedBci", "bytecode.sourceInfo[baseIndex + SOURCE_INFO_OFFSET_BCI]");
            b.declaration(type(int.class), "endBci", "encodedBci & 0xFFFF");
            b.statement("return endBci");
            return ex;
        }

        private CodeExecutableElement createGetSourceSection() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.SourceInformation, "getSourceSection");
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return AbstractBytecodeNode.createSourceSection(bytecode.sources, bytecode.sourceInfo, baseIndex)");
            return ex;
        }

        private CodeExecutableElement createGetChildren() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.SourceInformationTree, "getChildren");
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return children");
            return ex;
        }

        private CodeExecutableElement createContains() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(boolean.class), "contains");
            ex.addParameter(new CodeVariableElement(type.asType(), "other"));
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return this.getStartIndex() <= other.getStartIndex() && other.getEndIndex() <= this.getEndIndex()");
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

            // Create the root node.
            b.declaration(type(int.class), "baseIndex", "sourceInfo.length - SOURCE_INFO_LENGTH");
            b.startDeclaration(type.asType(), "root");
            b.startNew(type.asType()).string("bytecode").string("baseIndex").end();
            b.end();

            b.declaration(type.asType(), "current", "root");
            b.declaration(generic(ArrayDeque.class, type.asType()), "stack", "new ArrayDeque<>()");

            b.startDoBlock();
            // Create the next node.
            b.statement("baseIndex -= SOURCE_INFO_LENGTH");
            b.startDeclaration(type.asType(), "newNode");
            b.startNew(type.asType()).string("bytecode").string("baseIndex").end();
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

            b.startReturn();
            b.string("root");
            b.end();
            return withTruffleBoundary(ex);
        }

    }

    class LocalVariableImplFactory {

        private CodeTypeElement type;

        private CodeTypeElement create() {
            type = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL),
                            ElementKind.CLASS, null, "LocalVariableImpl");
            type.setSuperClass(types.LocalVariable);

            type.add(new CodeVariableElement(Set.of(FINAL), abstractBytecodeNode.asType(), "bytecode"));
            type.add(new CodeVariableElement(Set.of(FINAL), type(int.class), "baseIndex"));

            CodeExecutableElement constructor = type.add(createConstructorUsingFields(Set.of(), type, null));
            CodeTree tree = constructor.getBodyTree();
            CodeTreeBuilder b = constructor.createBuilder();
            b.startStatement().startSuperCall().staticReference(bytecodeRootNodesImpl.asType(), "VISIBLE_TOKEN").end().end();
            b.tree(tree);

            if (model.enableLocalScoping) {
                type.add(createGetStartIndex());
                type.add(createGetEndIndex());
            }
            type.add(createGetInfo());
            type.add(createGetName());
            type.add(createGetLocalIndex());
            type.add(createGetLocalOffset());
            type.add(createGetTypeProfile());

            return type;
        }

        private CodeExecutableElement createGetStartIndex() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.LocalVariable, "getStartIndex");
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return bytecode.locals[baseIndex + LOCALS_OFFSET_START_BCI]");
            return ex;
        }

        private CodeExecutableElement createGetEndIndex() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.LocalVariable, "getEndIndex");
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return bytecode.locals[baseIndex + LOCALS_OFFSET_END_BCI]");
            return ex;
        }

        private CodeExecutableElement createGetInfo() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.LocalVariable, "getInfo");
            CodeTreeBuilder b = ex.createBuilder();

            b.declaration(type(int.class), "infoId", "bytecode.locals[baseIndex + LOCALS_OFFSET_INFO]");
            b.startIf().string("infoId == -1").end().startBlock();
            b.returnNull();
            b.end().startElseBlock();
            b.startReturn().string(readConst("infoId", "bytecode.constants")).end();
            b.end();
            return ex;
        }

        private CodeExecutableElement createGetName() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.LocalVariable, "getName");
            CodeTreeBuilder b = ex.createBuilder();

            b.declaration(type(int.class), "nameId", "bytecode.locals[baseIndex + LOCALS_OFFSET_NAME]");
            b.startIf().string("nameId == -1").end().startBlock();
            b.returnNull();
            b.end().startElseBlock();
            b.startReturn().string(readConst("nameId", "bytecode.constants")).end();
            b.end();
            return ex;
        }

        private CodeExecutableElement createGetLocalIndex() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.LocalVariable, "getLocalIndex");
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return baseIndex / LOCALS_LENGTH");
            return ex;
        }

        private CodeExecutableElement createGetLocalOffset() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.LocalVariable, "getLocalOffset");
            CodeTreeBuilder b = ex.createBuilder();
            if (model.enableLocalScoping) {
                b.statement("return bytecode.locals[baseIndex + LOCALS_OFFSET_INDEX] - USER_LOCALS_START_IDX");
            } else {
                b.statement("return baseIndex / LOCALS_LENGTH");
            }
            return ex;
        }

        private CodeExecutableElement createGetTypeProfile() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.LocalVariable, "getTypeProfile");
            CodeTreeBuilder b = ex.createBuilder();

            if (model.usesBoxingElimination()) {
                if (model.enableLocalScoping) {
                    b.declaration(type(byte[].class), "localTags", "bytecode.getLocalTags()");
                    b.startIf().string("localTags == null").end().startBlock();
                    b.returnNull();
                    b.end();
                    b.statement("return FrameSlotKind.fromTag(localTags[getLocalIndex()])");
                } else {
                    b.startIf().string("bytecode instanceof CachedBytecodeNode").end().startBlock();
                    b.statement("return bytecode.getRoot().getFrameDescriptor().getSlotKind(getLocalOffset() + USER_LOCALS_START_IDX)");
                    b.end().startElseBlock();
                    b.returnNull();
                    b.end();
                }
            } else {
                b.returnNull();
            }
            return ex;
        }

    }

    class LocalVariableListFactory {

        private CodeTypeElement type;

        private CodeTypeElement create() {
            type = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL),
                            ElementKind.CLASS, null, "LocalVariableList");
            type.setSuperClass(generic(type(AbstractList.class), types.LocalVariable));
            type.add(new CodeVariableElement(Set.of(FINAL), abstractBytecodeNode.asType(), "bytecode"));
            type.add(createConstructorUsingFields(Set.of(), type, null));
            type.add(createGet());
            type.add(createSize());
            return type;
        }

        private CodeExecutableElement createGet() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(declaredType(List.class), "get", 1);
            ex.setReturnType(types.LocalVariable);
            ex.renameArguments("index");
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
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(declaredType(List.class), "size");
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return bytecode.locals.length / LOCALS_LENGTH");
            return ex;
        }

    }

    class InstructionImplFactory {

        private CodeTypeElement type;

        private CodeTypeElement abstractArgument;

        InstructionImplFactory() {
            type = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL),
                            ElementKind.CLASS, null, "InstructionImpl");
            type.setSuperClass(types.Instruction);
        }

        private void create() {
            type.add(new CodeVariableElement(Set.of(FINAL), abstractBytecodeNode.asType(), "bytecode"));
            type.add(new CodeVariableElement(Set.of(FINAL), type(int.class), "bci"));
            type.add(new CodeVariableElement(Set.of(FINAL), type(int.class), "opcode"));

            CodeExecutableElement constructor = type.add(createConstructorUsingFields(Set.of(), type, null));
            CodeTree tree = constructor.getBodyTree();
            CodeTreeBuilder b = constructor.createBuilder();
            b.startStatement().startSuperCall().staticReference(bytecodeRootNodesImpl.asType(), "VISIBLE_TOKEN").end().end();
            b.tree(tree);

            abstractArgument = type.add(new AbstractArgumentFactory().create());
            type.add(createGetBytecodeIndex());
            type.add(createGetBytecodeNode());
            type.add(createGetOperationCode());
            type.add(createGetLength());
            type.add(createGetArguments());
            type.add(createGetName());
            type.add(createIsInstrumentation());
            type.add(createNext());

            Set<String> generated = new HashSet<>();
            for (ImmediateKind kind : ImmediateKind.values()) {
                String className = getImmediateClassName(kind);
                if (generated.contains(className)) {
                    continue;
                }
                if (kind == ImmediateKind.TAG_NODE && !model.enableTagInstrumentation) {
                    continue;
                }
                CodeTypeElement implType = type.add(new ArgumentFactory(kind).create());
                abstractArgument.getPermittedSubclasses().add(implType.asType());
                generated.add(className);
            }
        }

        private CodeExecutableElement createGetBytecodeIndex() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.Instruction, "getBytecodeIndex");
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return bci");
            return ex;
        }

        private CodeExecutableElement createNext() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.Instruction, "next");
            CodeTreeBuilder b = ex.createBuilder();
            b.declaration(type(int.class), "nextBci", "bci + getLength()");
            b.startIf().string("nextBci >= bytecode.bytecodes.length").end().startBlock();
            b.returnNull();
            b.end();
            b.startReturn().startNew(type.asType()).string("bytecode").string("nextBci").string("bytecode.readValidBytecode(bytecode.bytecodes, nextBci)").end().end();
            return ex;
        }

        private CodeExecutableElement createGetBytecodeNode() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.Instruction, "getBytecodeNode");
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return bytecode");
            return ex;
        }

        private CodeExecutableElement createGetName() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.Instruction, "getName");
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
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.Instruction, "isInstrumentation");
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
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(FINAL), type(int.class), "getLength");
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
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.Instruction, "getArguments");
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
                    b.doubleQuote(immediate.name());
                    b.string("bci + " + immediate.offset());
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

        private Map<Boolean, List<InstructionModel>> groupInstructionsByInstrumentation(Collection<InstructionModel> models) {
            return models.stream().collect(Collectors.groupingBy(InstructionModel::isInstrumentation));
        }

        private Collection<List<InstructionModel>> groupInstructionsByLength(Collection<InstructionModel> models) {
            return models.stream().sorted(Comparator.comparingInt((i) -> i.getInstructionLength())).collect(Collectors.groupingBy((m) -> m.getInstructionLength())).values();
        }

        private Collection<List<InstructionModel>> groupInstructionsByImmediates(Collection<InstructionModel> models) {
            return models.stream().collect(Collectors.groupingBy((m) -> m.getImmediates())).values().stream().sorted(Comparator.comparingInt((i) -> i.get(0).getId())).toList();
        }

        private CodeExecutableElement createGetOperationCode() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.Instruction, "getOperationCode");
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
                case LOCAL_OFFSET:
                    return "LocalOffsetArgument";
                case INTEGER:
                case LOCAL_INDEX:
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

        class AbstractArgumentFactory {

            private CodeTypeElement type;

            AbstractArgumentFactory() {
            }

            private CodeTypeElement create() {
                type = new CodeTypeElement(Set.of(PRIVATE, SEALED, STATIC, ABSTRACT),
                                ElementKind.CLASS, null, "AbstractArgument");
                type.setSuperClass(types.Instruction_Argument);
                type.add(new CodeVariableElement(Set.of(FINAL), abstractBytecodeNode.asType(), "bytecode"));
                type.add(new CodeVariableElement(Set.of(FINAL), type(String.class), "name"));
                type.add(new CodeVariableElement(Set.of(FINAL), type(int.class), "bci"));
                CodeExecutableElement constructor = type.add(createConstructorUsingFields(Set.of(), type, null));
                CodeTree tree = constructor.getBodyTree();
                CodeTreeBuilder b = constructor.createBuilder();
                b.startStatement().startSuperCall().staticReference(bytecodeRootNodesImpl.asType(), "VISIBLE_TOKEN").end().end();
                b.tree(tree);

                type.add(createGetName());

                return type;
            }

            private CodeExecutableElement createGetName() {
                CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.Instruction_Argument, "getName");
                ex.getModifiers().add(Modifier.FINAL);
                CodeTreeBuilder b = ex.createBuilder();
                b.statement("return name");
                return ex;
            }

        }

        class ArgumentFactory {

            private CodeTypeElement type;
            private ImmediateKind immediateKind;

            ArgumentFactory(ImmediateKind immediateKind) {
                this.immediateKind = immediateKind;
            }

            private CodeTypeElement create() {
                type = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL),
                                ElementKind.CLASS, null, getImmediateClassName(immediateKind));
                type.setSuperClass(abstractArgument.asType());
                type.add(createConstructorUsingFields(Set.of(), type));
                type.add(createGetKind());

                switch (immediateKind) {
                    case BYTECODE_INDEX:
                        type.add(createAsBytecodeIndex());
                        break;
                    case INTEGER:
                    case LOCAL_INDEX:
                    case LOCAL_ROOT:
                    case STACK_POINTER:
                        type.add(createAsInteger());
                        break;
                    case LOCAL_OFFSET:
                        type.add(createAsLocalOffset());
                        break;
                    case CONSTANT:
                        type.add(createAsConstant());
                        break;
                    case NODE_PROFILE:
                        type.add(createAsNodeProfile());
                        break;
                    case BRANCH_PROFILE:
                        type.add(createAsBranchProfile());
                        break;
                    case TAG_NODE:
                        type.add(createAsTagNode());
                        break;
                    default:
                        throw new AssertionError("Unexpected kind");
                }

                return type;
            }

            private CodeExecutableElement createAsBytecodeIndex() {
                CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.Instruction_Argument, "asBytecodeIndex");
                ex.getModifiers().add(Modifier.FINAL);
                CodeTreeBuilder b = ex.createBuilder();
                b.declaration(type(short[].class), "bc", "this.bytecode.bytecodes");
                b.startReturn();
                b.string("bc[bci]");
                b.end();
                return ex;
            }

            private CodeExecutableElement createAsInteger() {
                CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.Instruction_Argument, "asInteger");
                ex.getModifiers().add(Modifier.FINAL);
                CodeTreeBuilder b = ex.createBuilder();
                b.declaration(type(short[].class), "bc", "this.bytecode.bytecodes");
                b.startReturn();
                b.string("bc[bci]");
                b.end();
                return ex;
            }

            private CodeExecutableElement createAsLocalOffset() {
                CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.Instruction_Argument, "asLocalOffset");
                ex.getModifiers().add(Modifier.FINAL);
                CodeTreeBuilder b = ex.createBuilder();
                b.declaration(type(short[].class), "bc", "this.bytecode.bytecodes");
                b.startReturn();
                b.string("bc[bci]").string(" - USER_LOCALS_START_IDX");
                b.end();
                return ex;
            }

            private CodeExecutableElement createAsConstant() {
                CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.Instruction_Argument, "asConstant");
                ex.getModifiers().add(Modifier.FINAL);
                CodeTreeBuilder b = ex.createBuilder();
                b.declaration(type(short[].class), "bc", "this.bytecode.bytecodes");
                b.declaration(type(Object[].class), "constants", "this.bytecode.constants");
                b.startReturn();
                b.string("constants[bc[bci]]");
                b.end();
                return ex;
            }

            private CodeExecutableElement createAsNodeProfile() {
                CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.Instruction_Argument, "asNodeProfile");
                ex.getModifiers().add(Modifier.FINAL);
                CodeTreeBuilder b = ex.createBuilder();
                b.declaration(arrayOf(types.Node), "cachedNodes", "this.bytecode.getCachedNodes()");
                b.startIf().string("cachedNodes == null").end().startBlock();
                b.statement("return null");
                b.end();
                b.declaration(type(short[].class), "bc", "this.bytecode.bytecodes");
                b.startReturn();
                b.tree(CodeTreeBuilder.singleString("cachedNodes[bc[bci]]"));
                b.end();
                return ex;
            }

            private CodeExecutableElement createAsTagNode() {
                CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.Instruction_Argument, "asTagNode");
                ex.getModifiers().add(Modifier.FINAL);
                CodeTreeBuilder b = ex.createBuilder();
                b.declaration(type(short[].class), "bc", "this.bytecode.bytecodes");
                b.declaration(tagRootNode.asType(), "tagRoot", "this.bytecode.tagRoot");
                b.startIf().string("tagRoot == null").end().startBlock();
                b.statement("return null");
                b.end();
                b.startReturn();
                b.tree(readTagNodeSafe(CodeTreeBuilder.singleString("bc[bci]")));
                b.end();
                return ex;
            }

            private CodeExecutableElement createAsBranchProfile() {
                CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.Instruction_Argument, "asBranchProfile");
                ex.getModifiers().add(Modifier.FINAL);
                CodeTreeBuilder b = ex.createBuilder();
                b.declaration(type(short[].class), "bc", "this.bytecode.bytecodes");
                b.declaration(type(int.class), "index", "bc[bci]");
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
                CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.Instruction_Argument, "getKind");
                ex.getModifiers().add(Modifier.FINAL);
                CodeTreeBuilder b = ex.createBuilder();
                b.startReturn();
                String name = switch (immediateKind) {
                    case BRANCH_PROFILE -> "BRANCH_PROFILE";
                    case BYTECODE_INDEX -> "BYTECODE_INDEX";
                    case CONSTANT -> "CONSTANT";
                    case LOCAL_OFFSET -> "LOCAL_OFFSET";
                    case INTEGER, LOCAL_INDEX, LOCAL_ROOT, STACK_POINTER -> "INTEGER";
                    case NODE_PROFILE -> "NODE_PROFILE";
                    case TAG_NODE -> "TAG_NODE";
                };
                b.staticReference(types.Instruction_Argument_Kind, name);
                b.end();
                return ex;
            }

        }

    }

    class TagNodeFactory {

        private CodeTypeElement type;

        TagNodeFactory() {
            type = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL),
                            ElementKind.CLASS, null, "TagNode");
            type.setSuperClass(types.TagTreeNode);
            type.getImplements().add(types.InstrumentableNode);
            type.getImplements().add(types.TagTree);

            type.add(new CodeVariableElement(Set.of(FINAL, STATIC), arrayOf(type.asType()), "EMPTY_ARRAY")).createInitBuilder().string("new TagNode[0]");

            type.add(new CodeVariableElement(Set.of(FINAL), type(int.class), "tags"));
            type.add(new CodeVariableElement(Set.of(FINAL), type(short.class), "enterBci"));

            type.add(createConstructorUsingFields(Set.of(), type));

            compFinal(type.add(new CodeVariableElement(Set.of(), type(short.class), "leaveBci")));
            child(type.add(new CodeVariableElement(Set.of(), arrayOf(type.asType()), "children")));

            child(type.add(new CodeVariableElement(Set.of(PRIVATE, VOLATILE), types.ProbeNode, "probe")));
            compFinal(type.add(new CodeVariableElement(Set.of(PRIVATE, VOLATILE), types.SourceSection, "sourceSection")));

        }

        private void create() {
            type.add(createCreateWrapper());
            type.add(createFindProbe());
            type.add(createIsInstrumentable());
            type.add(createHasTag());
            type.add(createGetSourceSection());
            type.add(createGetSourceSections());
            type.add(createCreateSourceSection());
            type.add(createFindBytecodeNode());
            type.addOptional(createDispatch());
            type.add(createGetLanguage());

            // TagTree
            type.add(createGetTreeChildren());
            type.add(createGetTags());
            type.add(createGetStartBci());
            type.add(createGetEndBci());

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

        private CodeExecutableElement createGetStartBci() {
            CodeExecutableElement ex = GeneratorUtils.override(types.TagTree, "getStartBci");
            ex.getModifiers().remove(Modifier.ABSTRACT);
            ex.getModifiers().add(Modifier.FINAL);
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return this.enterBci");
            return ex;
        }

        private CodeExecutableElement createGetEndBci() {
            CodeExecutableElement ex = GeneratorUtils.override(types.TagTree, "getEndBci");
            ex.getModifiers().remove(Modifier.ABSTRACT);
            ex.getModifiers().add(Modifier.FINAL);
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return this.leaveBci");
            return ex;
        }

        private CodeExecutableElement createCreateWrapper() {
            CodeExecutableElement ex = GeneratorUtils.override(types.InstrumentableNode, "createWrapper");
            ex.renameArguments("p");
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

            b.declaration(abstractBytecodeNode.asType(), "bytecode", "findBytecodeNode()");
            b.startReturn().string("bytecode.findSourceLocations(enterBci, leaveBci)").end();
            return ex;
        }

        private CodeExecutableElement createCreateSourceSection() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), types.SourceSection, "createSourceSection");
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn().string("findBytecodeNode().findSourceLocation(enterBci, leaveBci)").end();
            return ex;
        }

        private CodeExecutableElement createIsInstrumentable() {
            CodeExecutableElement ex = GeneratorUtils.override(types.InstrumentableNode, "isInstrumentable");
            ex.getModifiers().remove(Modifier.ABSTRACT);
            ex.getModifiers().add(Modifier.FINAL);
            ex.createBuilder().returnTrue();
            return ex;
        }

        private CodeExecutableElement createHasTag() {
            CodeExecutableElement ex = GeneratorUtils.override(types.InstrumentableNode, "hasTag");
            ex.renameArguments("tag");
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

    class AbstractCachedNode {

        private CodeTypeElement type;

        AbstractCachedNode() {
            type = new CodeTypeElement(Set.of(PRIVATE, STATIC, ABSTRACT),
                            ElementKind.CLASS, null, "AbstractCachedNode");
            type.setSuperClass(types.Node);
            type.add(createGetSourceSection());
            // TODO these InstrumentableNode methods should be removed
            // the debugger currently requires these methods to be present
            // as it does only accept source sections from instrumentable nodes.
            // for this to work we need to capture bci locations in
            // truffle stack trace elements
            if (model.enableTagInstrumentation) {
                type.getImplements().add(types.InstrumentableNode);
                type.add(createCreateWrapper());
                type.add(createIsInstrumentable());
                type.add(createFindProbe());
                type.add(createHasTag());
            }
        }

        private CodeTypeElement create() {
            return type;
        }

        private CodeExecutableElement createGetSourceSection() {
            CodeExecutableElement ex = GeneratorUtils.override(types.Node, "getSourceSection");
            ex.getModifiers().remove(Modifier.ABSTRACT);
            ex.getModifiers().add(Modifier.FINAL);
            CodeTreeBuilder b = ex.createBuilder();
            b.declaration("CachedBytecodeNode", "bytecode", "(CachedBytecodeNode) getParent()");
            b.declaration(type(int.class), "bci", "bytecode.findBytecodeIndexOfOperationNode(this)");
            b.startReturn().string("bytecode.findSourceLocation(bci)").end();
            return withTruffleBoundary(ex);
        }

        private CodeExecutableElement createCreateWrapper() {
            CodeExecutableElement ex = GeneratorUtils.override(types.InstrumentableNode, "createWrapper");
            ex.renameArguments("probe");
            ex.getModifiers().remove(Modifier.ABSTRACT);
            ex.getModifiers().add(Modifier.FINAL);
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return null");
            return ex;
        }

        private CodeExecutableElement createIsInstrumentable() {
            CodeExecutableElement ex = GeneratorUtils.override(types.InstrumentableNode, "isInstrumentable");
            ex.getModifiers().remove(Modifier.ABSTRACT);
            ex.getModifiers().add(Modifier.FINAL);
            ex.createBuilder().returnTrue();
            return ex;
        }

        private CodeExecutableElement createFindProbe() {
            CodeExecutableElement ex = GeneratorUtils.override(types.InstrumentableNode, "findProbe");
            ex.getModifiers().remove(Modifier.ABSTRACT);
            ex.getModifiers().add(Modifier.FINAL);
            CodeTreeBuilder b = ex.createBuilder();
            b.declaration(abstractBytecodeNode.asType(), "bytecode", "(AbstractBytecodeNode)getParent()");
            b.statement("return bytecode.tagRoot != null ? bytecode.tagRoot.getProbe() : null");
            return ex;
        }

        private CodeExecutableElement createHasTag() {
            CodeExecutableElement ex = GeneratorUtils.override(types.InstrumentableNode, "hasTag");
            ex.getModifiers().remove(Modifier.ABSTRACT);
            ex.getModifiers().add(Modifier.FINAL);
            ex.createBuilder().returnFalse();
            return ex;
        }

    }

    class TagRootNodeFactory {

        private CodeTypeElement type;

        TagRootNodeFactory() {
            type = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL),
                            ElementKind.CLASS, null, "TagRootNode");
            type.setSuperClass(types.Node);
            child(type.add(new CodeVariableElement(Set.of(), tagNode.asType(), "root")));
            type.add(new CodeVariableElement(Set.of(FINAL), arrayOf(tagNode.asType()), "tagNodes"));
        }

        private CodeTypeElement create() {
            type.add(GeneratorUtils.createConstructorUsingFields(Set.of(), type));

            child(type.add(new CodeVariableElement(Set.of(), types.ProbeNode, "probe")));
            CodeExecutableElement getProbe = type.add(new CodeExecutableElement(Set.of(), types.ProbeNode, "getProbe"));
            CodeTreeBuilder b = getProbe.createBuilder();
            b.declaration(types.ProbeNode, "localProbe", "this.probe");
            b.startIf().string("localProbe == null").end().startBlock();
            b.statement("this.probe = localProbe = root.createProbe(null)");
            b.end();
            b.statement("return localProbe");

            return type;
        }

    }

    class AbstractBytecodeNodeFactory {

        private CodeTypeElement type;
        private CodeExecutableElement continueAt;

        AbstractBytecodeNodeFactory() {
        }

        private CodeTypeElement create() {
            type = new CodeTypeElement(Set.of(PRIVATE, STATIC, ABSTRACT, SEALED),
                            ElementKind.CLASS, null, "AbstractBytecodeNode");
            type.setSuperClass(types.BytecodeNode);
            type.add(compFinal(1, new CodeVariableElement(Set.of(FINAL), arrayOf(type(short.class)), "bytecodes")));
            type.add(compFinal(1, new CodeVariableElement(Set.of(FINAL), arrayOf(type(Object.class)), "constants")));
            type.add(compFinal(1, new CodeVariableElement(Set.of(FINAL), arrayOf(type(int.class)), "handlers")));
            if (model.enableTracing) {
                type.add(compFinal(1, new CodeVariableElement(Set.of(PRIVATE, FINAL), context.getType(boolean[].class), "basicBlockBoundary")));
            }
            type.add(compFinal(1, new CodeVariableElement(Set.of(FINAL), context.getType(int[].class), "locals")));
            type.add(compFinal(1, new CodeVariableElement(Set.of(FINAL), context.getType(int[].class), "sourceInfo")));
            type.add(new CodeVariableElement(Set.of(FINAL), generic(type(List.class), types.Source), "sources"));
            type.add(new CodeVariableElement(Set.of(FINAL), type(int.class), "numNodes"));

            if (model.enableTagInstrumentation) {
                child(type.add(new CodeVariableElement(Set.of(), tagRootNode.asType(), "tagRoot")));
            }

            for (ExecutableElement superConstructor : ElementFilter.constructorsIn(ElementUtils.castTypeElement(types.BytecodeNode).getEnclosedElements())) {
                CodeExecutableElement constructor = CodeExecutableElement.cloneNoAnnotations(superConstructor);
                constructor.setReturnType(null);
                constructor.setSimpleName(type.getSimpleName());
                constructor.getParameters().remove(0);

                for (VariableElement var : ElementFilter.fieldsIn(type.getEnclosedElements())) {
                    constructor.addParameter(new CodeVariableElement(var.asType(), var.getSimpleName().toString()));
                }

                CodeTreeBuilder b = constructor.createBuilder();
                b.startStatement().startSuperCall().string("BytecodeRootNodesImpl.VISIBLE_TOKEN").end().end();
                for (VariableElement var : ElementFilter.fieldsIn(type.getEnclosedElements())) {
                    b.startStatement();
                    b.string("this.", var.getSimpleName().toString(), " = ", var.getSimpleName().toString());
                    b.end();
                }
                type.add(constructor);
                break;
            }

            if (model.enableTagInstrumentation) {
                type.getImplements().add(types.InstrumentableNode);
                type.add(createInstrumentableNodeCreateWrapper());
                type.add(createInstrumentableNodeIsInstrumentable());
                type.add(createInstrumentableNodeFindProbe());
                type.add(createInstrumentableNodeHasTag());
                type.add(createFindInstrumentableCallNode());
            }

            type.add(createReadValidBytecode());

            continueAt = type.add(new CodeExecutableElement(Set.of(ABSTRACT), type(int.class), "continueAt"));
            continueAt.addParameter(new CodeVariableElement(bytecodeNodeGen.asType(), "$root"));
            continueAt.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));
            if (model.enableYield) {
                continueAt.addParameter(new CodeVariableElement(types.VirtualFrame, "localFrame"));
            }
            continueAt.addParameter(new CodeVariableElement(context.getType(int.class), "startState"));

            var getRoot = type.add(new CodeExecutableElement(Set.of(FINAL), bytecodeNodeGen.asType(), "getRoot"));
            CodeTreeBuilder b = getRoot.createBuilder();
            b.startReturn().cast(bytecodeNodeGen.asType()).string("getParent()").end();

            var findLocation = type.add(new CodeExecutableElement(Set.of(STATIC), types.BytecodeLocation, "findLocation"));
            findLocation.addParameter(new CodeVariableElement(type.asType(), "node"));
            findLocation.addParameter(new CodeVariableElement(type(int.class), "bci"));
            b = findLocation.createBuilder();
            b.startReturn().startCall("node.findLocation").string("bci").end().end();

            type.add(new CodeExecutableElement(Set.of(ABSTRACT), type.asType(), "toCached"));
            CodeExecutableElement update = type.add(new CodeExecutableElement(Set.of(ABSTRACT), type.asType(), "update"));

            for (VariableElement e : ElementFilter.fieldsIn(type.getEnclosedElements())) {
                update.addParameter(new CodeVariableElement(e.asType(), e.getSimpleName().toString() + "_"));
            }

            if (model.isBytecodeUpdatable()) {
                type.add(createInvalidate());
                if (model.enableYield) {
                    type.add(createUpdateContinuationsWithoutInvalidate());
                }
            }

            type.add(createValidateBytecodes());
            type.add(createDumpInvalid());

            type.add(new CodeExecutableElement(Set.of(ABSTRACT), type.asType(), "cloneUninitialized"));

            type.add(new CodeExecutableElement(Set.of(ABSTRACT), arrayOf(types.Node), "getCachedNodes"));
            if (model.usesBoxingElimination() && model.enableLocalScoping) {
                type.add(new CodeExecutableElement(Set.of(ABSTRACT), arrayOf(type(byte.class)), "getLocalTags"));
            }
            type.add(new CodeExecutableElement(Set.of(ABSTRACT), arrayOf(type(int.class)), "getBranchProfiles"));

            // Define methods for introspecting the bytecode and source.
            type.add(createGetSourceSection());
            type.add(createFindSourceLocation());
            type.add(createFindSourceLocationBeginEnd());
            type.add(createFindSourceLocations());
            type.add(createFindSourceLocationsBeginEnd());
            type.add(createCreateSourceSection());
            type.add(createFindInstruction());
            type.add(createGetSourceInformation());
            type.add(createGetSourceInformationTree());
            type.add(createGetExceptionHandlers());
            type.add(createGetTagTree());

            type.add(createGetLocalCount());
            type.add(createGetLocalValue(type(Object.class)));
            type.add(createSetLocalValue(type(Object.class)));

            if (model.usesBoxingElimination()) {
                type.add(createSetLocalValueImpl(type(Object.class)));

                for (TypeMirror boxingType : model.boxingEliminatedTypes) {
                    type.add(createGetLocalValue(boxingType));
                    type.add(createSetLocalValue(boxingType));
                    type.add(createSetLocalValueImpl(boxingType));
                }

                type.add(createSpecializeSlotKind());
                type.add(createGetCachedLocalKind());
                type.add(createGetCachedLocalKindInternal());

                type.add(createSetCachedLocalKind());
                type.add(createSetCachedLocalKindInternal());
            }

            if (model.enableLocalScoping) {
                type.add(createResolveLocalsIndex());
            }

            type.add(createGetLocalName());
            type.add(createGetLocalInfo());
            type.add(createGetLocals());

            if (model.enableTagInstrumentation) {
                type.add(createGetTagNodes());
            }

            if (model.isBytecodeUpdatable()) {
                type.add(createTransitionState());
                type.add(createToStableBytecodeIndex());
                type.add(createFromStableBytecodeIndex());
                type.add(createTransitionInstrumentationIndex());
                type.add(createComputeNewBci());
            }
            type.add(createAdoptNodesAfterUpdate());

            return type;
        }

        private CodeExecutableElement createGetLocalCount() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeNode, "getLocalCount");
            ex.getModifiers().add(FINAL);
            ex.renameArguments("bci");
            CodeTreeBuilder b = ex.createBuilder();
            ex.getAnnotationMirrors().add(new CodeAnnotationMirror(types.ExplodeLoop));
            b.startStatement().startStaticCall(types.CompilerAsserts, "partialEvaluationConstant").string("bci").end().end();

            if (model.enableLocalScoping) {
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

        private CodeExecutableElement createGetLocalValue(TypeMirror specializedType) {
            boolean generic = ElementUtils.isObject(specializedType);
            String suffix;
            if (generic) {
                suffix = "";
            } else {
                suffix = ElementUtils.firstLetterUpperCase(ElementUtils.getSimpleName(specializedType));
            }

            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeNode, "getLocalValue" + suffix);
            ex.renameArguments("bci", "frame", "localOffset");
            ex.getModifiers().add(FINAL);

            CodeTreeBuilder b = ex.createBuilder();
            buildVerifyLocalsIndex(b);
            buildVerifyFrameDescriptor(b);

            b.declaration(type(int.class), "frameIndex", "USER_LOCALS_START_IDX + localOffset");
            if (model.usesBoxingElimination()) {
                if (generic) {
                    b.startTryBlock();
                    b.declaration(types.FrameSlotKind, "kind");
                    b.startIf().startStaticCall(types.CompilerDirectives, "inInterpreter").end().end().startBlock();
                    b.lineComment("Resolving the local index is expensive. Don't do it in the interpreter.");
                    b.startAssign("kind").startStaticCall(types.FrameSlotKind, "fromTag");
                    b.string("frame.getTag(frameIndex)");
                    b.end().end();

                    b.end().startElseBlock();

                    if (model.enableLocalScoping) {
                        b.startAssign("kind").string("getCachedLocalKind(frame, frameIndex, bci, localOffset)").end();
                    } else {
                        b.startAssign("kind").string("getCachedLocalKind(frame, frameIndex)").end();
                    }
                    b.end();

                    b.startSwitch().string("kind").end().startBlock();
                    for (TypeMirror boxingType : model.boxingEliminatedTypes) {
                        b.startCase().string(ElementUtils.firstLetterUpperCase(ElementUtils.getSimpleName(boxingType))).end();
                        b.startCaseBlock();

                        b.startReturn();
                        startExpectFrame(b, "frame", boxingType, false).string("frameIndex").end();
                        b.end();
                        b.end(); // case block
                    }

                    b.startCase().string("Object").end();
                    b.startCaseBlock();
                    b.startReturn();
                    startExpectFrame(b, "frame", type(Object.class), false).string("frameIndex").end();
                    b.end();
                    b.end(); // case block

                    b.startCase().string("Illegal").end();
                    b.startCaseBlock();
                    b.startReturn();
                    b.string("frame.getFrameDescriptor().getDefaultValue()");
                    b.end();
                    b.end(); // case block

                    b.caseDefault().startCaseBlock();
                    b.tree(GeneratorUtils.createShouldNotReachHere("unexpected slot"));
                    b.end();

                    b.end(); // switch block
                    b.end().startCatchBlock(types.UnexpectedResultException, "ex");
                    b.startReturn().string("ex.getResult()").end();
                    b.end(); // catch
                } else {
                    b.startReturn();
                    startExpectFrame(b, "frame", specializedType, false).string("frameIndex").end();
                    b.end();
                }
            } else {
                b.startReturn().string("frame.getObject(frameIndex)").end();
            }

            return ex;
        }

        private CodeExecutableElement createSetLocalValue(TypeMirror specializedType) {
            String suffix;
            if (ElementUtils.isObject(specializedType)) {
                suffix = "";
            } else {
                suffix = ElementUtils.firstLetterUpperCase(ElementUtils.getSimpleName(specializedType));
            }

            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeNode, "setLocalValue" + suffix);
            ex.renameArguments("bci", "frame", "localOffset", "value");
            CodeTreeBuilder b = ex.createBuilder();
            buildVerifyLocalsIndex(b);
            buildVerifyFrameDescriptor(b);
            b.declaration(type(int.class), "frameIndex", "USER_LOCALS_START_IDX + localOffset");
            if (model.usesBoxingElimination()) {
                b.startStatement().startCall("setLocalValue" + suffix + "Impl");
                b.string("frame").string("frameIndex").string("value");
                if (model.enableLocalScoping) {
                    b.string("bci").string("localOffset");
                }
                b.end().end(); // call, statement
            } else {
                b.startStatement();
                b.startCall("frame", getSetMethod(specializedType)).string("frameIndex").string("value").end();
                b.end();
            }
            return ex;
        }

        private CodeExecutableElement createSetLocalValueImpl(TypeMirror specializedType) {
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

            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "setLocalValue" + suffix + "Impl");
            ex.addParameter(new CodeVariableElement(types.Frame, "frame"));
            ex.addParameter(new CodeVariableElement(type(int.class), "frameIndex"));
            ex.addParameter(new CodeVariableElement(specializedType, "value"));

            CodeTreeBuilder b = ex.createBuilder();
            buildVerifyFrameDescriptor(b);

            if (model.enableLocalScoping) {
                ex.addParameter(new CodeVariableElement(type(int.class), "bci"));
                ex.addParameter(new CodeVariableElement(type(int.class), "localOffset"));
                b.declaration(types.FrameSlotKind, "oldKind", "getCachedLocalKind(frame, frameIndex, bci, localOffset)");
            } else {
                b.declaration(types.FrameSlotKind, "oldKind", "getCachedLocalKind(frame, frameIndex)");
            }
            b.declaration(types.FrameSlotKind, "newKind");

            b.startSwitch().string("oldKind").end().startBlock();

            for (TypeMirror boxingType : model.boxingEliminatedTypes) {
                if (!generic && !ElementUtils.typeEquals(boxingType, specializedType)) {
                    continue;
                }

                b.startCase().string(ElementUtils.firstLetterUpperCase(ElementUtils.getSimpleName(boxingType))).end();
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
                    b.startAssign("newKind").staticReference(types.FrameSlotKind, "Object").end();
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

            b.startCase().string(ElementUtils.firstLetterUpperCase(ElementUtils.getSimpleName(type(Object.class)))).end();
            b.startCaseBlock();
            b.startStatement();
            b.startCall("frame", getSetMethod(type(Object.class))).string("frameIndex").string("value").end();
            b.end();
            b.statement("return");
            b.end(); // case block
            b.caseDefault().startCaseBlock();
            b.startAssign("newKind").string("specializeSlotKind(value)").end();
            b.statement("break");
            b.end();
            b.end(); // switch block

            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
            if (model.enableLocalScoping) {
                b.statement("setCachedLocalKind(frameIndex, newKind, bci, localOffset)");
                b.statement("setLocalValueImpl(frame, frameIndex, value, bci, localOffset)");
            } else {
                b.statement("setCachedLocalKind(frameIndex, newKind)");
                b.statement("setLocalValueImpl(frame, frameIndex, value)");
            }

            return ex;
        }

        private CodeExecutableElement createGetCachedLocalKind() {
            if (!model.usesBoxingElimination()) {
                throw new AssertionError("Not supported.");
            }

            CodeExecutableElement ex = new CodeExecutableElement(Set.of(FINAL), types.FrameSlotKind, "getCachedLocalKind");
            ex.addParameter(new CodeVariableElement(types.Frame, "frame"));
            ex.addParameter(new CodeVariableElement(type(int.class), "frameIndex"));

            CodeTreeBuilder b = ex.createBuilder();

            if (model.enableLocalScoping) {
                ex.addParameter(new CodeVariableElement(type(int.class), "bci"));
                ex.addParameter(new CodeVariableElement(type(int.class), "localOffset"));
                b.startAssert().string("locals[resolveLocalsIndex(bci, localOffset) + LOCALS_OFFSET_INDEX] == frameIndex : ").doubleQuote("Inconsistent indices.").end();

                b.declaration(type(byte.class), "tag");
                b.declaration(type(byte[].class), "localTags", "getLocalTags()");
                b.startIf().string("localTags == null").end().startBlock();
                b.startReturn().staticReference(types.FrameSlotKind, "Object").end();
                b.end().startElseBlock();
                b.startReturn().startStaticCall(types.FrameSlotKind, "fromTag").string("localTags[resolveLocalsIndex(bci, localOffset) / LOCALS_LENGTH]").end().end();
                b.end();
            } else {
                b.statement("return getRoot().getFrameDescriptor().getSlotKind(frameIndex)");
            }
            return ex;
        }

        private CodeExecutableElement createGetCachedLocalKindInternal() {
            if (!model.usesBoxingElimination()) {
                throw new AssertionError("Not supported.");
            }

            CodeExecutableElement ex = new CodeExecutableElement(Set.of(FINAL), types.FrameSlotKind, "getCachedLocalKindInternal");
            ex.addParameter(new CodeVariableElement(types.Frame, "frame"));
            ex.addParameter(new CodeVariableElement(type(int.class), "frameIndex"));

            CodeTreeBuilder b = ex.createBuilder();
            if (model.enableLocalScoping) {
                ex.addParameter(new CodeVariableElement(type(int.class), "localIndex"));
                b.startAssert().string("locals[(localIndex * LOCALS_LENGTH) + LOCALS_OFFSET_INDEX] == frameIndex : ").doubleQuote("Inconsistent indices.").end();
                b.declaration(type(byte.class), "tag");
                b.declaration(type(byte[].class), "localTags", "getLocalTags()");
                b.startAssert().string("localTags != null").end();
                b.startReturn().startStaticCall(types.FrameSlotKind, "fromTag").string("localTags[localIndex]").end().end();
            } else {
                b.statement("return getRoot().getFrameDescriptor().getSlotKind(frameIndex)");
            }
            return ex;
        }

        private CodeExecutableElement createSetCachedLocalKind() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "setCachedLocalKind");
            ex.addParameter(new CodeVariableElement(type(int.class), "frameIndex"));
            ex.addParameter(new CodeVariableElement(types.FrameSlotKind, "kind"));
            CodeTreeBuilder b = ex.createBuilder();
            if (model.enableLocalScoping) {
                ex.addParameter(new CodeVariableElement(type(int.class), "bci"));
                ex.addParameter(new CodeVariableElement(type(int.class), "localOffset"));
                b.startAssert().string("locals[resolveLocalsIndex(bci, localOffset) + LOCALS_OFFSET_INDEX] == frameIndex : ").doubleQuote("Inconsistent indices.").end();

                b.declaration(type(byte.class), "tag");
                b.declaration(type(byte[].class), "localTags", "getLocalTags()");
                b.startIf().string("localTags == null").end().startBlock();
                b.lineComment("Method not yet cached.");
                b.statement("return");
                b.end().startElseBlock();
                b.statement("localTags[resolveLocalsIndex(bci, localOffset) / LOCALS_LENGTH] = kind.tag");
                b.end();
            } else {
                b.statement("getRoot().getFrameDescriptor().setSlotKind(frameIndex, kind)");
            }

            return ex;
        }

        private CodeExecutableElement createSetCachedLocalKindInternal() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(FINAL), type(void.class), "setCachedLocalKindInternal");
            ex.addParameter(new CodeVariableElement(type(int.class), "frameIndex"));
            ex.addParameter(new CodeVariableElement(types.FrameSlotKind, "kind"));
            CodeTreeBuilder b = ex.createBuilder();
            if (model.enableLocalScoping) {
                ex.addParameter(new CodeVariableElement(type(int.class), "localIndex"));
                b.startAssert().string("locals[(localIndex * LOCALS_LENGTH) + LOCALS_OFFSET_INDEX] == frameIndex : ").doubleQuote("Inconsistent indices.").end();
                b.declaration(type(byte.class), "tag");
                b.declaration(type(byte[].class), "localTags", "getLocalTags()");
                b.startIf().string("localTags == null").end().startBlock();
                b.lineComment("bytecode node not yet cached.");
                b.statement("return");
                b.end().startElseBlock();
                b.statement("localTags[localIndex] = kind.tag");
                b.end();
            } else {
                b.statement("getRoot().getFrameDescriptor().setSlotKind(frameIndex, kind)");
            }

            return ex;
        }

        private CodeExecutableElement createSpecializeSlotKind() {
            if (!model.usesBoxingElimination()) {
                throw new AssertionError("Not supported.");
            }

            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), types.FrameSlotKind, "specializeSlotKind");
            ex.addParameter(new CodeVariableElement(type(Object.class), "value"));
            CodeTreeBuilder b = ex.createBuilder();
            boolean elseIf = false;
            for (TypeMirror boxingType : model.boxingEliminatedTypes) {
                elseIf = b.startIf(elseIf);
                b.string("value instanceof ").type(ElementUtils.boxType(boxingType)).end().startBlock();
                b.startReturn().staticReference(types.FrameSlotKind, ElementUtils.firstLetterUpperCase(ElementUtils.getSimpleName(boxingType))).end();
                b.end();
            }
            b.startElseBlock();
            b.startReturn().staticReference(types.FrameSlotKind, "Object").end();
            b.end();

            return ex;
        }

        private void buildVerifyLocalsIndex(CodeTreeBuilder b) {
            b.startStatement().startStaticCall(types.CompilerAsserts, "partialEvaluationConstant").string("bci").end().end();
            b.startStatement().startStaticCall(types.CompilerAsserts, "partialEvaluationConstant").string("localOffset").end().end();
            b.startAssert().string("localOffset >= 0 && localOffset < getLocalCount(bci) : ").doubleQuote("Invalid out-of-bounds local offset provided.").end();
        }

        private void buildVerifyFrameDescriptor(CodeTreeBuilder b) {
            b.startAssert().string("getRoot().getFrameDescriptor() == frame.getFrameDescriptor() : ").doubleQuote("Invalid frame with invalid descriptor passed.").end();
        }

        private CodeExecutableElement createGetLocalName() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeNode, "getLocalName");
            ex.renameArguments("bci", "localOffset");
            CodeTreeBuilder b = ex.createBuilder();
            buildVerifyLocalsIndex(b);
            if (model.enableLocalScoping) {
                b.declaration(type(int.class), "index", "resolveLocalsIndex(bci, localOffset)");
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
            b.startReturn().string(readConst("nameId")).end();
            b.end();
            return ex;
        }

        private CodeExecutableElement createResolveLocalsIndex() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(int.class), "resolveLocalsIndex");
            ex.addParameter(new CodeVariableElement(type(int.class), "bci"));
            ex.addParameter(new CodeVariableElement(type(int.class), "localOffset"));
            ex.addAnnotationMirror(new CodeAnnotationMirror(types.ExplodeLoop));
            CodeTreeBuilder b = ex.createBuilder();
            if (model.enableLocalScoping) {
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
            } else {
                b.statement("return localOffset");
            }
            return ex;
        }

        private CodeExecutableElement createGetLocalInfo() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeNode, "getLocalInfo");
            ex.renameArguments("bci", "localOffset");
            CodeTreeBuilder b = ex.createBuilder();
            buildVerifyLocalsIndex(b);

            if (model.enableLocalScoping) {
                b.declaration(type(int.class), "index", "resolveLocalsIndex(bci, localOffset)");
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
            b.startReturn().string(readConst("infoId")).end();
            b.end();

            return ex;
        }

        private CodeExecutableElement createGetLocals() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeNode, "getLocals");
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn().startNew("LocalVariableList").string("this").end().end();
            return ex;
        }

        private CodeExecutableElement createValidateBytecodes() {
            CodeExecutableElement validate = new CodeExecutableElement(Set.of(PRIVATE, FINAL), type(boolean.class), "validateBytecodes");
            CodeTreeBuilder b = validate.createBuilder();

            b.declaration(bytecodeNodeGen.asType(), "root");
            b.declaration(arrayOf(type(short.class)), "bc", "this.bytecodes");
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

            b.startWhile().string("bci < bc.length").end().startBlock();
            b.startTryBlock();
            b.startSwitch().string("bc[bci]").end().startBlock();

            for (InstructionModel instr : model.getInstructions()) {
                b.startCase().tree(createInstructionConstant(instr)).end().startBlock();

                // instruction data array

                for (InstructionImmediate immediate : instr.getImmediates()) {
                    // argument data array
                    String readImmediate = "bc[bci + " + immediate.offset() + "]";
                    String localName = immediate.name();
                    b.declaration(type(int.class), localName, readImmediate);

                    switch (immediate.kind()) {
                        case BYTECODE_INDEX:
                            b.startIf();
                            if (acceptsInvalidChildBci(instr)) {
                                // supports -1 immediates
                                b.string(localName, " < -1");
                            } else {
                                b.string(localName, " < 0");
                            }
                            b.string(" || ").string(localName).string(" >= bc.length").end().startBlock();
                            b.tree(createValidationError("bytecode index is out of bounds"));
                            b.end();
                            break;
                        case INTEGER:
                        case STACK_POINTER:
                            break;
                        case LOCAL_OFFSET: {
                            InstructionImmediate root = instr.getImmediate(ImmediateKind.LOCAL_ROOT);
                            if (root == null) {
                                b.startAssign("root").string("this.getRoot()").end();
                            } else {
                                b.startAssign("root").string("this.getRoot().getBytecodeRootNodeImpl(", readBc("bci + " + root.offset()), ")").end();
                            }
                            b.startIf().string(localName).string(" - USER_LOCALS_START_IDX").string(" < 0 || ").string(localName).string(" - USER_LOCALS_START_IDX").string(
                                            " >= root.numLocals").end().startBlock();
                            b.tree(createValidationError("local offset is out of bounds"));
                            b.end();
                            break;
                        }
                        case LOCAL_INDEX: {
                            InstructionImmediate root = instr.getImmediate(ImmediateKind.LOCAL_ROOT);
                            if (root == null) {
                                b.startAssign("root").string("this.getRoot()").end();
                            } else {
                                b.startAssign("root").string("this.getRoot().getBytecodeRootNodeImpl(", readBc("bci + " + root.offset()), ")").end();
                            }
                            b.startIf().string(localName).string(" < 0 || ").string(localName).string(" >= root.bytecode.locals.length").end().startBlock();
                            b.tree(createValidationError("local index is out of bounds"));
                            b.end();
                            break;
                        }
                        case LOCAL_ROOT:
                            // checked via LOCAL_OFFSET and LOCAL_INDEX
                            break;
                        case CONSTANT:
                            b.startIf().string(localName).string(" < 0 || ").string(localName).string(" >= constants.length").end().startBlock();
                            b.tree(createValidationError("constant is out of bounds"));
                            b.end();
                            break;
                        case NODE_PROFILE:
                            b.startIf().string(localName).string(" < 0 || ").string(localName).string(" >= numNodes").end().startBlock();
                            b.tree(createValidationError("node profile is out of bounds"));
                            b.end();
                            break;
                        case BRANCH_PROFILE:
                            b.startIf().string("branchProfiles != null").end().startBlock();
                            b.startIf().string(localName).string(" < 0 || ").string(localName).string(" >= branchProfiles.length").end().startBlock();
                            b.tree(createValidationError("branch profile is out of bounds"));
                            b.end();
                            b.end();
                            break;
                        case TAG_NODE:
                            b.startIf().string("tagNodes != null").end().startBlock();
                            b.declaration(tagNode.asType(), "node", readTagNodeSafe(CodeTreeBuilder.singleString(immediate.name())));
                            b.startIf().string("node == null").end().startBlock();
                            b.tree(createValidationError("tagNode is null"));
                            b.end();
                            b.end();
                            break;
                        default:
                            throw new AssertionError("Unexpected kind");
                    }
                }

                b.statement("bci = bci + " + instr.getInstructionLength());
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
            b.tree(createValidationError(null, "e"));
            b.end();

            b.end(); // while

            b.declaration(arrayOf(type(int.class)), "ex", "this.handlers");

            b.startIf().string("ex.length % EXCEPTION_HANDLER_LENGTH != 0").end().startBlock();
            b.tree(createValidationError("exception handler table size is incorrect"));
            b.end();

            b.startFor().string("int i = 0; i < ex.length; i = i + EXCEPTION_HANDLER_LENGTH").end().startBlock();
            b.declaration(type(int.class), "startBci", "ex[i + EXCEPTION_HANDLER_OFFSET_START_BCI]");
            b.declaration(type(int.class), "endBci", "ex[i + EXCEPTION_HANDLER_OFFSET_END_BCI]");
            b.declaration(type(int.class), "handlerBci", "ex[i + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI]");
            b.declaration(type(int.class), "spStart", "ex[i + EXCEPTION_HANDLER_OFFSET_STACK_POINTER]");
            b.declaration(type(int.class), "exceptionLocal", "ex[i + EXCEPTION_HANDLER_OFFSET_LOCAL]");

            b.startIf().string("startBci").string(" < 0 || ").string("startBci").string(" >= bc.length").end().startBlock();
            b.tree(createValidationError("startBci index is out of bounds"));
            b.end();

            // exclusive
            b.startIf().string("endBci").string(" < 0 || ").string("endBci").string(" > bc.length").end().startBlock();
            b.tree(createValidationError("endBci index is out of bounds"));
            b.end();

            b.startSwitch().string("exceptionLocal").end().startBlock();
            if (model.epilogExceptional != null) {
                b.startCase().string("HANDLER_EPILOG_EXCEPTIONAL").end().startCaseBlock();

                b.startIf().string("handlerBci").string(" != -1").end().startBlock();
                b.tree(createValidationError("handlerBci index is invalid"));
                b.end();

                b.startIf().string("spStart").string(" != -1").end().startBlock();
                b.tree(createValidationError("handlerBci index is invalid"));
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
            b.startIf().string("handlerBci").string(" < 0 || ").string("handlerBci").string(" >= bc.length").end().startBlock();
            b.tree(createValidationError("handlerBci index is out of bounds"));
            b.end();

            b.startIf().string("exceptionLocal").string(" < 0 || ").string("exceptionLocal").string(" >= getRoot().numLocals").end().startBlock();
            b.tree(createValidationError("exceptionLocal index is out of bounds"));
            b.end();
            b.statement("break");
            b.end(); // case default

            b.end(); // switch
            b.end(); // for handler

            b.startReturn().string("true").end();

            return validate;
        }

        /**
         * Returns true if the instruction can take -1 as a child bci.
         */
        private boolean acceptsInvalidChildBci(InstructionModel instr) {
            if (instr.isShortCircuitConverter() || instr.isEpilogReturn()) {
                return true;
            }
            if (instr.getQuickeningRoot() == model.popInstruction && //
                            (!instr.isQuickening() || //
                                            ElementUtils.typeEquals(instr.signature.getSpecializedType(0), context.getDeclaredType(Object.class)))) {
                // For pop, if we don't know the child bci we'll quicken to generic.
                // Pops with specialized types should have valid child bci's, though.
                return true;
            }
            return false;
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
            return createValidationError(message, null);
        }

        private CodeTree createValidationError(String message, String cause) {
            CodeTreeBuilder b = new CodeTreeBuilder(null);
            b.startThrow().startStaticCall(types.CompilerDirectives, "shouldNotReachHere");
            b.startGroup();
            b.startStaticCall(type(String.class), "format");
            if (message == null) {
                b.doubleQuote("Bytecode validation error at index: %s.%n%s");
            } else {
                b.doubleQuote("Bytecode validation error at index: %s. " + message + "%n%s");
            }
            b.string("bci");
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
            b.startFor().string("int i = 0; i < localHandlers.length; i += 5").end().startBlock();
            b.startIf().string("localHandlers[i] > bci").end().startBlock().statement("continue").end();
            b.startIf().string("localHandlers[i + 1] <= bci").end().startBlock().statement("continue").end();
            b.statement("int local = localHandlers[i + 4]");
            b.startIf().string("local != HANDLER_TAG_EXCEPTIONAL").end().startBlock();
            b.statement("continue");
            b.end();
            b.statement("int nodeId = localHandlers[i + 2]");
            b.statement("return tagRoot.tagNodes[nodeId]");
            b.end();
            b.statement("return null");
            return ex;
        }

        private CodeExecutableElement createInstrumentableNodeCreateWrapper() {
            CodeExecutableElement ex = GeneratorUtils.override(types.InstrumentableNode, "createWrapper");
            ex.renameArguments("probe");
            ex.getModifiers().remove(Modifier.ABSTRACT);
            ex.getModifiers().add(Modifier.FINAL);
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return null");
            return ex;
        }

        private CodeExecutableElement createInstrumentableNodeIsInstrumentable() {
            CodeExecutableElement ex = GeneratorUtils.override(types.InstrumentableNode, "isInstrumentable");
            ex.getModifiers().remove(Modifier.ABSTRACT);
            ex.getModifiers().add(Modifier.FINAL);
            ex.createBuilder().returnTrue();
            return ex;
        }

        private CodeExecutableElement createInstrumentableNodeFindProbe() {
            CodeExecutableElement ex = GeneratorUtils.override(types.InstrumentableNode, "findProbe");
            ex.getModifiers().remove(Modifier.ABSTRACT);
            ex.getModifiers().add(Modifier.FINAL);
            ex.createBuilder().statement("return this.tagRoot != null ? this.tagRoot.getProbe() : null");
            return ex;
        }

        private CodeExecutableElement createInstrumentableNodeHasTag() {
            CodeExecutableElement ex = GeneratorUtils.override(types.InstrumentableNode, "hasTag");
            ex.getModifiers().remove(Modifier.ABSTRACT);
            ex.getModifiers().add(Modifier.FINAL);
            ex.createBuilder().returnFalse();
            return ex;
        }

        private CodeExecutableElement createReadValidBytecode() {
            CodeExecutableElement method = new CodeExecutableElement(
                            Set.of(FINAL),
                            type(int.class), "readValidBytecode",
                            new CodeVariableElement(type(short[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"));
            CodeTreeBuilder b = method.createBuilder();
            if (model.isBytecodeUpdatable()) {
                b.declaration(type(int.class), "op", readBc("bci"));
                b.startSwitch().string("op").end().startBlock();
                for (InstructionModel instruction : model.getInvalidateInstructions()) {
                    b.startCase().tree(createInstructionConstant(instruction)).end();
                }
                b.startCaseBlock();
                b.lineComment("While we were processing the exception handler the code invalidated.");
                b.lineComment("We need to re-read the op from the old bytecodes.");
                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                b.statement("return oldBytecodes[bci]");
                b.end(); // case
                b.caseDefault().startCaseBlock();
                b.statement("return op");
                b.end();
                b.end(); // switch
            } else {
                mergeSuppressWarnings(method, "static-method");
                b.lineComment("The bytecode is not updatable so the bytecode is always valid.");
                b.startReturn().string(readBc("bci")).end();
            }
            return method;
        }

        private CodeExecutableElement createGetTagNodes() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), arrayOf(tagNode.asType()), "getTagNodes");
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn().string("tagRoot != null ? tagRoot.tagNodes : null").end();
            return ex;
        }

        private CodeExecutableElement createTransitionState() {
            CodeExecutableElement invalidate = new CodeExecutableElement(Set.of(FINAL), type(int.class), "transitionState");
            invalidate.addParameter(new CodeVariableElement(type.asType(), "newBytecode"));
            invalidate.addParameter(new CodeVariableElement(type(int.class), "state"));
            if (model.enableYield) {
                invalidate.addParameter(new CodeVariableElement(continuationRootNodeImpl.asType(), "continuationRootNode"));
            }

            CodeTreeBuilder b = invalidate.createBuilder();

            b.declaration(arrayOf(type(short.class)), "oldBc", "this.oldBytecodes");
            b.declaration(arrayOf(type(short.class)), "newBc", "newBytecode.bytecodes");

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

            b.startIf().string("this == newBytecode || this.bytecodes == newBc").end().startBlock();
            b.lineComment("No change in bytecodes.");
            b.startReturn().string("state").end();
            b.end();

            b.statement("assert oldBc != null");

            b.declaration(type(int.class), "oldBci", "state & 0xFFFF");

            b.startDeclaration(type(int.class), "newBci");
            b.startCall("computeNewBci").string("oldBci").string("oldBc").string("newBc");
            if (model.enableTagInstrumentation) {
                b.string("this.getTagNodes()");
                b.string("newBytecode.getTagNodes()");
            }
            b.end(); // call

            b.end();

            if (model.specializationDebugListener) {
                b.startStatement();
                b.startCall("getRoot().onBytecodeStackTransition");
                emitParseInstruction(b, "this", "oldBci", CodeTreeBuilder.singleString("oldBc[oldBci]"));
                emitParseInstruction(b, "newBytecode", "newBci", CodeTreeBuilder.singleString("newBc[newBci]"));
                b.end().end();
            }

            b.startReturn().string("(state & 0xFFFF_0000) | (newBci & 0x0000_FFFF)").end();

            return invalidate;
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
            invalidate.addParameter(new CodeVariableElement(arrayOf(type(short.class)), "oldBc"));
            invalidate.addParameter(new CodeVariableElement(type(int.class), "oldBciBase"));
            invalidate.addParameter(new CodeVariableElement(type(int.class), "oldBciTarget"));
            invalidate.addParameter(new CodeVariableElement(arrayOf(type(short.class)), "newBc"));
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
            b.declaration(type(short.class), "op", "ACCESS.shortArrayRead(oldBc, oldBci)");
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
            b.declaration(type(short.class), "op", "ACCESS.shortArrayRead(oldBc, oldBci)");
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
            b.declaration(type(short.class), "op", "ACCESS.shortArrayRead(newBc, newBci)");
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
            ex.addParameter(new CodeVariableElement(arrayOf(type(short.class)), "oldBc"));
            ex.addParameter(new CodeVariableElement(arrayOf(type(short.class)), "newBc"));
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
            b.startSwitch().string(readBc("bci")).end().startBlock();

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
                            .collect(Collectors.groupingBy(constructor)).entrySet() //
                            .stream().sorted(Comparator.comparing(e -> e.getKey())).toList();
        }

        private CodeExecutableElement createToStableBytecodeIndex() {
            CodeExecutableElement translate = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(int.class), "toStableBytecodeIndex");
            translate.addParameter(new CodeVariableElement(arrayOf(type(short.class)), "bc"));
            translate.addParameter(new CodeVariableElement(type(int.class), "searchBci"));
            emitStableBytecodeSearch(translate.createBuilder(), "searchBci", "stableBci", true);
            return translate;
        }

        private CodeExecutableElement createFromStableBytecodeIndex() {
            CodeExecutableElement translate = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(int.class), "fromStableBytecodeIndex");
            translate.addParameter(new CodeVariableElement(arrayOf(type(short.class)), "bc"));
            translate.addParameter(new CodeVariableElement(type(int.class), "stableSearchBci"));
            emitStableBytecodeSearch(translate.createBuilder(), "stableSearchBci", "stableBci", false);
            return translate;
        }

        private record InvalidateInstructionGroup(int instructionLength, boolean isYield) {
            InvalidateInstructionGroup(InstructionModel instr) {
                this(instr.getInstructionLength(), instr.kind == InstructionKind.YIELD);
            }
        }

        private CodeExecutableElement createInvalidate() {
            CodeExecutableElement invalidate = new CodeExecutableElement(Set.of(FINAL), type(void.class), "invalidate");
            invalidate.addParameter(new CodeVariableElement(type.asType(), "newNode"));
            invalidate.addParameter(new CodeVariableElement(type(CharSequence.class), "reason"));
            if (model.enableYield) {
                invalidate.addParameter(new CodeVariableElement(generic(ArrayList.class, continuationLocation.asType()), "continuationLocations"));
            }
            CodeTreeBuilder b = invalidate.createBuilder();

            b.declaration(arrayOf(type(short.class)), "bc", "this.bytecodes");
            b.declaration(type(int.class), "bci", "0");
            if (model.enableYield) {
                b.declaration(type(int.class), "continuationIndex", "0");
            }

            b.startAssign("this.oldBytecodes").startStaticCall(type(Arrays.class), "copyOf").string("bc").string("bc.length").end().end();

            b.startStatement().startStaticCall(type(VarHandle.class), "loadLoadFence").end().end();

            b.startWhile().string("bci < bc.length").end().startBlock();
            b.declaration(type(short.class), "op", "bc[bci]");
            b.startSwitch().string("op").end().startBlock();
            Map<InvalidateInstructionGroup, List<InstructionModel>> instructionsByLength = model.getInstructions().stream().collect(Collectors.groupingBy(InvalidateInstructionGroup::new));

            for (var entry : instructionsByLength.entrySet()) {
                InvalidateInstructionGroup group = entry.getKey();
                int length = group.instructionLength;
                List<InstructionModel> instructions = entry.getValue();
                if (instructions.isEmpty()) {
                    continue;
                }
                for (InstructionModel instruction : instructions) {
                    b.startCase().tree(createInstructionConstant(instruction)).end();
                }
                b.startCaseBlock();
                if (group.isYield) {
                    b.startDeclaration(type(int.class), "constantPoolIndex");
                    b.tree(readImmediate("bc", "bci", instructions.get(0).getImmediate(ImmediateKind.CONSTANT)));
                    b.end();

                    b.startDeclaration(continuationRootNodeImpl.asType(), "continuationRootNode");
                    b.cast(continuationRootNodeImpl.asType());
                    b.string("constants[constantPoolIndex]");
                    b.end();

                    b.declaration(continuationLocation.asType(), "continuationLocation", "continuationLocations.get(continuationIndex++)");
                    b.startAssert().string("continuationLocation.constantPoolIndex == constantPoolIndex").end();
                    b.startStatement().startCall("continuationRootNode", "updateBytecodeLocation");
                    b.string("newNode.getBytecodeLocation(continuationLocation.bci)");
                    b.string("this");
                    b.string("newNode");
                    b.string("reason");
                    b.end(2);
                }
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

        private CodeExecutableElement createUpdateContinuationsWithoutInvalidate() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(FINAL), type(void.class), "updateContinuationsWithoutInvalidate");
            ex.addParameter(new CodeVariableElement(type.asType(), "newNode"));
            ex.addParameter(new CodeVariableElement(generic(ArrayList.class, continuationLocation.asType()), "continuationLocations"));
            CodeTreeBuilder b = ex.createBuilder();

            b.declaration(arrayOf(type(short.class)), "bc", "this.bytecodes");
            b.declaration(type(int.class), "bci", "0");
            b.declaration(type(int.class), "continuationIndex", "0");

            b.startWhile().string("bci < bc.length").end().startBlock();
            b.declaration(type(short.class), "op", "bc[bci]");
            b.startSwitch().string("op").end().startBlock();
            Map<InvalidateInstructionGroup, List<InstructionModel>> instructionsByLength = model.getInstructions().stream().collect(Collectors.groupingBy(InvalidateInstructionGroup::new));

            for (var entry : instructionsByLength.entrySet()) {
                InvalidateInstructionGroup group = entry.getKey();
                int length = group.instructionLength;
                List<InstructionModel> instructions = entry.getValue();
                if (instructions.isEmpty()) {
                    continue;
                }
                for (InstructionModel instruction : instructions) {
                    b.startCase().tree(createInstructionConstant(instruction)).end();
                }
                b.startCaseBlock();
                if (group.isYield) {
                    b.startDeclaration(type(int.class), "constantPoolIndex");
                    b.tree(readImmediate("bc", "bci", instructions.get(0).getImmediate(ImmediateKind.CONSTANT)));
                    b.end();

                    b.startDeclaration(continuationRootNodeImpl.asType(), "continuationRootNode");
                    b.cast(continuationRootNodeImpl.asType());
                    b.string("constants[constantPoolIndex]");
                    b.end();

                    b.declaration(continuationLocation.asType(), "continuationLocation", "continuationLocations.get(continuationIndex++)");
                    b.startAssert().string("continuationLocation.constantPoolIndex == constantPoolIndex").end();
                    b.startStatement().startCall("continuationRootNode", "updateBytecodeLocationWithoutInvalidate");
                    b.string("newNode.getBytecodeLocation(continuationLocation.bci)");
                    b.end(2);
                }
                b.statement("bci += " + length);
                b.statement("break");
                b.end();
            }

            b.end();
            b.end(); // switch
            b.end(); // while

            return ex;
        }

        private CodeExecutableElement createAdoptNodesAfterUpdate() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), type(void.class), "adoptNodesAfterUpdate");
            CodeTreeBuilder b = ex.createBuilder();
            b.lineComment("no nodes to adopt");
            return ex;
        }

        private CodeExecutableElement createFindSourceLocation() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeNode, "findSourceLocation", 1);
            ex.getModifiers().add(FINAL);
            ex.renameArguments("bci");
            CodeTreeBuilder b = ex.createBuilder();

            b.declaration(arrayOf(type(int.class)), "info", "this.sourceInfo");
            b.declaration(generic(type(List.class), types.Source), "localSources", "this.sources");
            b.startIf().string("info == null").end().startBlock();
            b.declaration(type.asType(), "newNode", "getRoot().ensureSources()");
            b.statement("info = newNode.sourceInfo");
            b.statement("localSources = newNode.sources");
            b.end();

            b.startFor().string("int i = 0; i < info.length; i += SOURCE_INFO_LENGTH").end().startBlock();
            b.declaration(type(int.class), "encodedBci", "info[i + SOURCE_INFO_OFFSET_BCI]");
            b.declaration(type(int.class), "beginBci", "(encodedBci >> 16) & 0xFFFF");
            b.declaration(type(int.class), "endBci", "encodedBci & 0xFFFF");

            b.startIf().string("bci >= beginBci && bci < endBci").end().startBlock();
            b.startReturn().string("createSourceSection(localSources, info, i)").end();
            b.end();

            b.end();

            b.startReturn().string("null").end();
            return ex;
        }

        private CodeExecutableElement createFindSourceLocationBeginEnd() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeNode, "findSourceLocation", 2);
            ex.getModifiers().add(FINAL);
            ex.renameArguments("searchBeginBci", "searchEndBci");

            CodeTreeBuilder b = ex.createBuilder();
            b.declaration(arrayOf(type(int.class)), "info", "this.sourceInfo");
            b.declaration(generic(type(List.class), types.Source), "localSources", "this.sources");
            b.startIf().string("info == null").end().startBlock();
            b.declaration(type.asType(), "newNode", "getRoot().ensureSources()");
            b.statement("info = newNode.sourceInfo");
            b.statement("localSources = newNode.sources");
            b.end();

            b.startFor().string("int i = 0; i < info.length; i += SOURCE_INFO_LENGTH").end().startBlock();
            b.declaration(type(int.class), "encodedBci", "info[i + SOURCE_INFO_OFFSET_BCI]");
            b.declaration(type(int.class), "beginBci", "(encodedBci >> 16) & 0xFFFF");
            b.declaration(type(int.class), "endBci", "encodedBci & 0xFFFF");

            b.startIf().string("searchBeginBci >= beginBci && searchEndBci <= endBci").end().startBlock();
            b.startReturn().string("createSourceSection(localSources, info, i)").end();
            b.end();

            b.end();

            b.startReturn().string("null").end();
            return ex;
        }

        private CodeExecutableElement createFindSourceLocations() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeNode, "findSourceLocations", 1);
            ex.getModifiers().add(FINAL);
            ex.renameArguments("bci");
            CodeTreeBuilder b = ex.createBuilder();

            b.startReturn().startCall("findSourceLocations");
            b.string("bci");
            b.string("bci + 1"); // searchEndBci is inclusive.
            b.end(2);
            return ex;
        }

        private CodeExecutableElement createFindSourceLocationsBeginEnd() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeNode, "findSourceLocations", 2);
            ex.getModifiers().add(FINAL);
            ex.renameArguments("searchBeginBci", "searchEndBci");
            CodeTreeBuilder b = ex.createBuilder();

            b.declaration(arrayOf(type(int.class)), "info", "this.sourceInfo");
            b.declaration(generic(type(List.class), types.Source), "localSources", "this.sources");
            b.startIf().string("info == null").end().startBlock();
            b.declaration(type.asType(), "newNode", "getRoot().ensureSources()");
            b.statement("info = newNode.sourceInfo");
            b.statement("localSources = newNode.sources");
            b.end();

            b.declaration(type(int.class), "sectionIndex", "0");
            b.startDeclaration(arrayOf(types.SourceSection), "sections").startNewArray(arrayOf(types.SourceSection), CodeTreeBuilder.singleString("8")).end().end();

            b.startFor().string("int i = 0; i < info.length; i += SOURCE_INFO_LENGTH").end().startBlock();
            b.declaration(type(int.class), "encodedBci", "info[i + SOURCE_INFO_OFFSET_BCI]");
            b.declaration(type(int.class), "beginBci", "(encodedBci >> 16) & 0xFFFF");
            b.declaration(type(int.class), "endBci", "encodedBci & 0xFFFF");

            b.startIf().string("searchBeginBci >= beginBci && searchEndBci <= endBci").end().startBlock();

            b.startIf().string("sectionIndex == sections.length").end().startBlock();
            b.startAssign("sections").startStaticCall(type(Arrays.class), "copyOf");
            b.string("sections");
            // Double the size of the array, but cap it at the number of source section entries.
            b.startStaticCall(type(Math.class), "min").string("sections.length * 2").string("info.length / SOURCE_INFO_LENGTH").end();
            b.end(2); // assign
            b.end(); // if

            b.startStatement().string("sections[sectionIndex++] = createSourceSection(localSources, info, i)").end();

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

            b.startAssert().string("sourceIndex >= 0 && sourceIndex < sources.size() : ").doubleQuote("source index out of bounds").end();
            b.startAssert().string("start >= 0 : ").doubleQuote("invalid source start index").end();
            b.startAssert().string("length >= 0 : ").doubleQuote("invalid source length").end();
            b.startReturn().string("sources.get(sourceIndex).createSection(start, length)").end();
            return ex;
        }

        private CodeExecutableElement createGetSourceSection() {
            CodeExecutableElement ex = GeneratorUtils.override(types.Node, "getSourceSection");
            ex.getAnnotationMirrors().add(new CodeAnnotationMirror(types.CompilerDirectives_TruffleBoundary));
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn().string("findSourceLocation(0, bytecodes.length)").end();
            return ex;
        }

        private CodeExecutableElement createFindInstruction() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeNode, "findInstruction");
            ex.renameArguments("bci");
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn();
            b.startNew(instructionImpl.asType());
            b.string("this").string("bci").string("readValidBytecode(this.bytecodes, bci)");
            b.end();
            b.end();
            return ex;
        }

        private CodeExecutableElement createGetSourceInformation() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeNode, "getSourceInformation");
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
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeNode, "getSourceInformationTree");
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
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeNode, "getExceptionHandlers");
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn();
            b.startNew("ExceptionHandlerList").string("this").end();
            b.end();
            return ex;
        }

        private CodeExecutableElement createGetTagTree() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeNode, "getTagTree");
            ex.renameArguments("bci");
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

    class BytecodeNodeFactory {
        private InterpreterTier tier;
        private CodeTypeElement type;
        private Map<InstructionModel, CodeExecutableElement> doInstructionMethods = new LinkedHashMap<>();

        BytecodeNodeFactory(InterpreterTier tier) {
            this.tier = tier;
        }

        private CodeTypeElement create() {
            type = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, tier.bytecodeClassName());
            type.setSuperClass(abstractBytecodeNode.asType());
            type.addAll(createContinueAt());
            type.getAnnotationMirrors().add(new CodeAnnotationMirror(types.DenyReplace));

            if (tier.isUncached()) {
                type.add(GeneratorUtils.createConstructorUsingFields(Set.of(), type));
                type.add(new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "uncachedExecuteCount_")).createInitBuilder().string("16");
            } else if (tier.isCached()) {
                type.add(createCachedConstructor());
                type.add(compFinal(1, new CodeVariableElement(Set.of(PRIVATE), arrayOf(types.Node), "cachedNodes_")));
                type.add(compFinal(1, new CodeVariableElement(Set.of(PRIVATE, FINAL), arrayOf(context.getType(int.class)), "branchProfiles_")));
                type.add(compFinal(1, new CodeVariableElement(Set.of(PRIVATE, FINAL), arrayOf(context.getType(boolean.class)), "exceptionProfiles_")));
                if (model.epilogExceptional != null) {
                    type.add(child(new CodeVariableElement(Set.of(PRIVATE), getCachedDataClassType(model.epilogExceptional.operation.instruction), "epilogExceptionalNode_")));
                }

                if (model.enableLocalScoping && model.usesBoxingElimination()) {
                    type.add(compFinal(1, new CodeVariableElement(Set.of(PRIVATE, FINAL), arrayOf(type(byte.class)), "localTags_")));
                }

                type.add(createLoadConstantCompiled());
                type.add(createAdoptNodesAfterUpdate());
                // Define the members required to support OSR.
                type.getImplements().add(types.BytecodeOSRNode);
                type.addAll(new OSRMembersFactory().create());
            } else if (tier.isUninitialized()) {
                type.add(GeneratorUtils.createConstructorUsingFields(Set.of(), type));
            } else {
                throw new AssertionError("invalid tier");
            }

            type.add(createSetUncachedThreshold());
            type.add(createGetTier());

            if (!tier.isUninitialized()) {
                // uninitialized does not need a copy constructor as the default constructor is
                // already copying.
                type.add(createCopyConstructor());
                type.add(createResolveThrowable());
                type.add(createResolveHandler());

                if (model.epilogExceptional != null) {
                    type.add(createDoEpilogExceptional());
                }
                if (model.enableTagInstrumentation) {
                    type.add(createDoTagExceptional());
                }
                if (model.interceptControlFlowException != null) {
                    type.add(createResolveControlFlowException());
                }
            }

            if (model.enableLocalScoping && model.usesBoxingElimination()) {
                type.add(createGetLocalTags());
            }

            type.add(createToCached());
            type.add(createUpdate());
            type.add(createCloneUninitialized());
            if (cloneUninitializedNeedsUnquickenedBytecode()) {
                type.add(createUnquickenBytecode());
            }
            type.add(createGetCachedNodes());
            type.add(createGetBranchProfiles());
            type.add(createFindBytecodeIndex1());
            type.add(createFindBytecodeIndex2());
            if (model.storeBciInFrame) {
                type.add(createGetBytecodeIndex());
            }
            type.add(createFindBytecodeIndexOfOperationNode());
            type.add(createToString());

            return type;
        }

        private boolean useOperationNodeForBytecodeIndex() {
            return !model.storeBciInFrame && tier == InterpreterTier.CACHED;
        }

        private boolean useFrameForBytecodeIndex() {
            return model.storeBciInFrame || tier == InterpreterTier.UNCACHED;
        }

        private CodeExecutableElement createFindBytecodeIndex1() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeNode, "findBytecodeIndex", 1);
            ex.renameArguments("frameInstance");
            CodeTreeBuilder b = ex.createBuilder();

            if (useOperationNodeForBytecodeIndex()) {
                b.declaration(types.Node, "prev", "null");
                b.startFor().string("Node current = frameInstance.getCallNode(); current != null; current = current.getParent()").end().startBlock();
                b.startIf().string("current == this && prev != null").end().startBlock();
                b.statement("return findBytecodeIndexOfOperationNode(prev)");
                b.end();
                b.statement("prev = current");
                b.end();
            }

            if (useFrameForBytecodeIndex()) {
                CodeTree getFrame = CodeTreeBuilder.createBuilder() //
                                .startCall("frameInstance", "getFrame") //
                                .staticReference(types.FrameInstance_FrameAccess, "READ_ONLY") //
                                .end().build();
                if (model.enableYield) {
                    /**
                     * If the frame is from a continuation, the bci will be in the locals frame,
                     * which is stored in slot COROUTINE_FRAME_IDX.
                     */
                    b.declaration(types.Frame, "frame", getFrame);
                    b.startDeclaration(types.Frame, "coroutineFrame");
                    b.cast(types.Frame).startCall("frame.getObject").string(COROUTINE_FRAME_IDX).end();
                    b.end();

                    b.startIf().string("coroutineFrame != null").end().startBlock();
                    b.startAssign("frame").string("coroutineFrame").end();
                    b.end();

                    b.startReturn();
                    b.startCall("frame", "getInt");
                    b.string(BCI_IDX);
                    b.end(2);
                } else {
                    b.startReturn();
                    b.startCall(getFrame, "getInt");
                    b.string(BCI_IDX);
                    b.end(2);
                }
            } else {
                b.startReturn().string("-1").end();
            }

            return withTruffleBoundary(ex);
        }

        private CodeExecutableElement createFindBytecodeIndex2() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeNode, "findBytecodeIndex", 2);
            ex.renameArguments("frame", "node");
            CodeTreeBuilder b = ex.createBuilder();

            if (useOperationNodeForBytecodeIndex()) {
                b.startIf().string("node != null").end().startBlock();
                b.statement("return findBytecodeIndexOfOperationNode(node)");
                b.end();
            }

            if (useFrameForBytecodeIndex()) {
                b.startReturn().string("frame.getInt(" + BCI_IDX + ")").end();
            } else {
                b.startReturn().string("-1").end();
            }

            return ex;
        }

        private CodeExecutableElement createGetBytecodeIndex() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeNode, "getBytecodeIndex");
            ex.renameArguments("frame");
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn().string("frame.getInt(" + BCI_IDX + ")").end();
            return ex;
        }

        private CodeExecutableElement createGetLocalTags() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement((DeclaredType) abstractBytecodeNode.asType(), "getLocalTags");
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
            CodeExecutableElement ex = GeneratorUtils.overrideImplement((DeclaredType) abstractBytecodeNode.asType(), "getCachedNodes");
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
            CodeExecutableElement ex = GeneratorUtils.overrideImplement((DeclaredType) abstractBytecodeNode.asType(), "getBranchProfiles");
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
            CodeExecutableElement ex = GeneratorUtils.overrideImplement((DeclaredType) abstractBytecodeNode.asType(), "cloneUninitialized");
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn();
            b.startNew(InterpreterTier.CACHED.friendlyName + "BytecodeNode");
            for (VariableElement var : ElementFilter.fieldsIn(abstractBytecodeNode.getEnclosedElements())) {
                if (cloneUninitializedNeedsUnquickenedBytecode() && var.getSimpleName().contentEquals("bytecodes")) {
                    b.startCall("unquickenBytecode").string("this.bytecodes").end();
                } else {
                    b.string("this.", var.getSimpleName().toString());
                }
            }
            b.end();
            b.end();
            return ex;
        }

        private CodeExecutableElement createUnquickenBytecode() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), arrayOf(type(short.class)), "unquickenBytecode");
            ex.addParameter(new CodeVariableElement(arrayOf(type(short.class)), "original"));

            CodeTreeBuilder b = ex.createBuilder();
            b.declaration(arrayOf(type(short.class)), "copy", "Arrays.copyOf(original, original.length)");

            Map<Boolean, List<InstructionModel>> partitionedByIsQuickening = model.getInstructions().stream() //
                            .collect(Collectors.partitioningBy(InstructionModel::isQuickening));
            List<Entry<Integer, List<InstructionModel>>> regularGroupedByLength = partitionedByIsQuickening.get(false).stream() //
                            .collect(Collectors.groupingBy(InstructionModel::getInstructionLength)).entrySet() //
                            .stream().sorted(Comparator.comparing(entry -> entry.getKey())) //
                            .toList();
            List<Entry<InstructionModel, List<InstructionModel>>> quickenedGroupedByQuickeningRoot = partitionedByIsQuickening.get(true).stream() //
                            .collect(Collectors.groupingBy(InstructionModel::getQuickeningRoot)).entrySet() //
                            .stream().sorted(Comparator.comparing((Entry<InstructionModel, List<InstructionModel>> entry) -> entry.getKey().isCustomInstruction()) //
                                            .thenComparing(entry -> entry.getKey().getInstructionLength())) //
                            .toList();

            b.declaration(type(int.class), "bci", "0");

            b.startWhile().string("bci < copy.length").end().startBlock();
            b.startSwitch().string(readBc("copy", "bci")).end().startBlock();

            for (var quickenedGroup : quickenedGroupedByQuickeningRoot) {
                InstructionModel quickeningRoot = quickenedGroup.getKey();
                List<InstructionModel> instructions = quickenedGroup.getValue();
                int instructionLength = instructions.get(0).getInstructionLength();
                for (InstructionModel instruction : instructions) {
                    assert instruction.getInstructionLength() == instructionLength;
                    b.startCase().tree(createInstructionConstant(instruction)).end();
                }
                b.startCaseBlock();

                b.startStatement().startCall("ACCESS.shortArrayWrite").string("copy").string("bci").tree(createInstructionConstant(quickeningRoot)).end(2);
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
            CodeExecutableElement ex = GeneratorUtils.overrideImplement((DeclaredType) abstractBytecodeNode.asType(), "toCached");
            CodeTreeBuilder b = ex.createBuilder();
            switch (tier) {
                case UNCACHED:
                case UNINITIALIZED:
                    b.startReturn();
                    b.startNew(InterpreterTier.CACHED.friendlyName + "BytecodeNode");
                    for (VariableElement var : ElementFilter.fieldsIn(abstractBytecodeNode.getEnclosedElements())) {
                        b.string("this.", var.getSimpleName().toString());
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
            CodeExecutableElement ex = new CodeExecutableElement(null, type.getSimpleName().toString());
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

            for (VariableElement var : ElementFilter.fieldsIn(type.getEnclosedElements())) {
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
            CodeExecutableElement ex = GeneratorUtils.overrideImplement((DeclaredType) abstractBytecodeNode.asType(), "update");
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
                b.startNew(type.asType());
                for (VariableElement e : ElementFilter.fieldsIn(abstractBytecodeNode.getEnclosedElements())) {
                    if (e.getModifiers().contains(STATIC)) {
                        continue;
                    }
                    b.string(e.getSimpleName().toString() + "__");
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
                b.startNew(type.asType());
                for (VariableElement e : ElementFilter.fieldsIn(abstractBytecodeNode.getEnclosedElements())) {
                    if (e.getModifiers().contains(STATIC)) {
                        continue;
                    }
                    b.string(e.getSimpleName().toString() + "__");
                }
                for (VariableElement e : ElementFilter.fieldsIn(type.getEnclosedElements())) {
                    if (e.getModifiers().contains(STATIC)) {
                        continue;
                    }
                    b.string("this.", e.getSimpleName().toString());
                }
                b.end();
                b.end();

            } else {
                b.startReturn();
                b.startNew(type.asType());
                for (VariableElement e : ElementFilter.fieldsIn(abstractBytecodeNode.getEnclosedElements())) {
                    b.string(e.getSimpleName().toString() + "__");
                }
                for (VariableElement e : ElementFilter.fieldsIn(type.getEnclosedElements())) {
                    b.string("this.", e.getSimpleName().toString());
                }
                b.end();
                b.end();
            }

            b.end(); // else
            return ex;
        }

        private CodeExecutableElement createGetTier() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeNode, "getTier");
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
                mergeSuppressWarnings(ex, "static-method");
                b.startReturn().string("-1").end();
                return ex;
            }

            b.startAssert().string("operationNode.getParent() == this : ").doubleQuote("Passed node must be an operation node of the same bytecode node.").end();
            b.declaration(arrayOf(types.Node), "localNodes", "this.cachedNodes_");
            b.declaration(arrayOf(type(short.class)), "bc", "this.bytecodes");
            b.statement("int bci = 0");
            b.string("loop: ").startWhile().string("bci < bc.length").end().startBlock();
            b.declaration(context.getType(int.class), "currentBci", "bci");
            b.declaration(context.getType(int.class), "nodeIndex");
            b.startSwitch().string(readBc("bci")).end().startBlock();

            Map<Boolean, List<InstructionModel>> instructionsGroupedByHasNode = model.getInstructions().stream().collect(Collectors.partitioningBy(InstructionModel::hasNodeImmediate));
            Map<Integer, List<InstructionModel>> nodelessGroupedByLength = instructionsGroupedByHasNode.get(false).stream().collect(Collectors.groupingBy(InstructionModel::getInstructionLength));

            record LengthAndNodeIndex(int length, int nodeIndex) {
            }
            Map<LengthAndNodeIndex, List<InstructionModel>> nodedGroupedByLengthAndNodeIndex = instructionsGroupedByHasNode.get(true).stream() //
                            .collect(Collectors.groupingBy(insn -> new LengthAndNodeIndex(insn.getInstructionLength(), insn.getImmediate(ImmediateKind.NODE_PROFILE).offset())));

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
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(context.getDeclaredType(Object.class), "toString");
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

        private CodeExecutableElement createCachedConstructor() {

            record CachedInitializationKey(int instructionLength, List<InstructionImmediate> immediates, String nodeName) {
                CachedInitializationKey(InstructionModel instr) {
                    this(instr.getInstructionLength(), instr.getImmediates().stream().filter((i) -> needsCachedInitialization(i)).toList(), cachedDataClassName(instr));
                }
            }

            CodeExecutableElement ex = GeneratorUtils.createConstructorUsingFields(Set.of(), type);

            TypeMirror nodeArrayType = new ArrayCodeTypeMirror(types.Node);

            CodeTreeBuilder b = ex.appendBuilder();

            b.tree(createNeverPartOfCompilation());
            b.declaration(nodeArrayType, "result", "new Node[this.numNodes]");
            b.statement("short[] bc = bytecodes");
            b.statement("int bci = 0");
            b.statement("int numConditionalBranches = 0");
            b.string("loop: ").startWhile().string("bci < bc.length").end().startBlock();
            b.startSwitch().string(readBc("bci")).end().startBlock();

            Map<CachedInitializationKey, List<InstructionModel>> grouped = model.getInstructions().stream()//
                            .filter((i -> !i.isQuickening())) //
                            .collect(Collectors.groupingBy(CachedInitializationKey::new));

            for (var entry : grouped.entrySet()) {
                CachedInitializationKey key = entry.getKey();
                List<InstructionModel> instructions = entry.getValue();
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

                b.statement("bci += " + key.instructionLength());
                b.statement("continue loop");
                b.end();
            }

            b.caseDefault().startBlock();
            emitThrowAssertionError(b, "\"Should not reach here\"");
            b.end();

            b.end(); // } switch
            b.end(); // } while

            b.startAssert().string("bci == bc.length").end();
            b.startAssign("this.cachedNodes_").string("result").end();
            b.startAssign("this.branchProfiles_").startStaticCall(types.BytecodeSupport, "allocateBranchProfiles").string("numConditionalBranches").end(2);
            b.startAssign("this.exceptionProfiles_").string("handlers.length == 0 ? EMPTY_EXCEPTION_PROFILES : new boolean[handlers.length / 5]").end();

            if (model.epilogExceptional != null) {
                b.startAssign("this.epilogExceptionalNode_").startCall("insert").startNew(getCachedDataClassType(model.epilogExceptional.operation.instruction)).end().end().end();
            }

            if (model.enableLocalScoping && model.usesBoxingElimination()) {
                b.declaration(type(byte[].class), "localTags", "new byte[this.locals.length / LOCALS_LENGTH]");
                b.statement("Arrays.fill(localTags, FrameSlotKind.Illegal.tag)");
                b.startAssign("this.localTags_").string("localTags").end();
            }

            type.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(boolean[].class), "EMPTY_EXCEPTION_PROFILES")).createInitBuilder().string("new boolean[0]");
            return ex;
        }

        private CodeExecutableElement createSetUncachedThreshold() {
            CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeNode, "setUncachedThreshold");
            GeneratorUtils.addOverride(ex);
            ex.renameArguments("invocationCount");
            ElementUtils.setVisibility(ex.getModifiers(), PUBLIC);
            ex.getModifiers().remove(ABSTRACT);

            CodeTreeBuilder b = ex.createBuilder();
            if (tier.isUncached()) {
                b.startAssign("uncachedExecuteCount_").string("invocationCount").end();
            } else {
                // do nothing for cached
            }
            return ex;
        }

        private List<CodeExecutableElement> createContinueAt() {
            // This method returns a list containing the continueAt method plus helper methods for
            // custom instructions. The helper methods help reduce the bytecode size of the dispatch
            // loop.
            List<CodeExecutableElement> results = new ArrayList<>();

            CodeExecutableElement ex = new CodeExecutableElement(Set.of(FINAL), context.getType(int.class), "continueAt");
            GeneratorUtils.addOverride(ex);
            ex.addAnnotationMirror(new CodeAnnotationMirror(types.HostCompilerDirectives_BytecodeInterpreterSwitch));
            ex.addParameter(new CodeVariableElement(bytecodeNodeGen.asType(), "$root"));
            ex.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));
            if (model.enableYield) {
                ex.addParameter(new CodeVariableElement(types.VirtualFrame, "localFrame"));
            }
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "startState"));

            results.add(ex);

            CodeTreeBuilder b = ex.createBuilder();
            if (tier.isUninitialized()) {
                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                b.statement("$root.transitionToCached()");
                b.startReturn().string("startState").end();
                return results;
            }
            if (tier.isUncached()) {
                b.startDeclaration(types.EncapsulatingNodeReference, "encapsulatingNode").startStaticCall(types.EncapsulatingNodeReference, "getCurrent").end().end();
                b.startDeclaration(types.Node, "prev").startCall("encapsulatingNode", "set").string("this").end().end();
                b.startTryBlock();

                b.statement("int uncachedExecuteCount = this.uncachedExecuteCount_");
                b.startIf().string("uncachedExecuteCount <= 0").end().startBlock();
                b.statement("$root.transitionToCached()");
                b.startReturn().string("startState").end();
                b.end();

            }

            b.declaration(new ArrayCodeTypeMirror(type(short.class)), "bc", "this.bytecodes");

            if (tier.isCached()) {
                b.declaration(new ArrayCodeTypeMirror(types.Node), "cachedNodes", "this.cachedNodes_");
                b.declaration(new ArrayCodeTypeMirror(type(int.class)), "branchProfiles", "this.branchProfiles_");
                ex.addAnnotationMirror(createExplodeLoopAnnotation("MERGE_EXPLODE"));
            }

            b.statement("int bci = startState & 0xFFFF");
            b.statement("int sp = (startState >> 16) & 0xFFFF");
            b.declaration(loopCounter.asType(), "loopCounter", CodeTreeBuilder.createBuilder().startNew(loopCounter.asType()).end());
            if (model.needsBciSlot() && !model.storeBciInFrame && !tier.isUncached()) {
                // If a bci slot is allocated but not used for non-uncached interpreters, set it to
                // an invalid value just in case it gets read during a stack walk.
                b.statement("ACCESS.setInt(" + localFrame() + ", " + BCI_IDX + ", -1)");
            }

            if (model.enableTracing) {
                b.declaration(context.getType(boolean[].class), "basicBlockBoundary", "this.basicBlockBoundary");
                b.declaration(types.ExecutionTracer, "tracer",
                                CodeTreeBuilder.createBuilder().startStaticCall(types.ExecutionTracer, "get").typeLiteral(bytecodeNodeGen.asType()).end());
                b.statement("tracer.startRoot(this)");

                b.startTryBlock();
            }

            b.string("loop: ").startWhile().string("true").end().startBlock();

            if (model.enableTracing) {
                b.startIf().string("basicBlockBoundary[bci]").end().startBlock();
                b.statement("tracer.traceStartBasicBlock(bci)");
                b.end();
            }

            b.startTryBlock();

            b.startSwitch().string(readBc("bci")).end().startBlock();

            for (InstructionModel instr : model.getInstructions()) {
                if (instr.isQuickening() && tier.isUncached()) {
                    continue;
                }

                if (!isInstructionReachable(instr)) {
                    continue;
                }

                b.startCase().tree(createInstructionConstant(instr)).end();
                if (tier.isUncached()) {
                    for (InstructionModel quickendInstruction : instr.getFlattenedQuickenedInstructions()) {
                        b.startCase().tree(createInstructionConstant(quickendInstruction)).end();
                    }
                }
                b.startBlock();

                if (model.enableTracing) {
                    b.startStatement().startCall("tracer.traceInstruction");
                    b.string("bci");
                    b.string(instr.getId());
                    b.string(String.valueOf(instr.signature != null && instr.signature.isVariadic));
                    b.end(2);
                }

                switch (instr.kind) {
                    case BRANCH:
                        b.statement("bci = " + readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.BYTECODE_INDEX)));
                        b.statement("continue loop");
                        break;
                    case BRANCH_BACKWARD:
                        if (tier.isUncached()) {
                            b.startIf().string("--uncachedExecuteCount <= 0").end().startBlock();
                            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                            b.statement("$root.transitionToCached()");
                            b.statement("return (sp << 16) | " + readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.BYTECODE_INDEX)));
                            b.end();
                        } else {
                            emitReportLoopCount(b, CodeTreeBuilder.createBuilder().string("++loopCounter.value >= ").staticReference(loopCounter.asType(), "REPORT_LOOP_STRIDE").build(), true);

                            b.startIf().startStaticCall(types.CompilerDirectives, "inInterpreter").end(1).string(" && ") //
                                            .startStaticCall(types.BytecodeOSRNode, "pollOSRBackEdge").string("this").end(2).startBlock();

                            if (model.enableYield) {
                                /**
                                 * If this invocation was resumed, the locals are no longer in the
                                 * stack frame and we need to pass the local frame to executeOSR.
                                 *
                                 * If this invocation was not resumed, the locals are still in the
                                 * stack frame. We pass null to signal that executeOSR should use
                                 * the stack frame (which may be virtualized by Bytecode OSR code).
                                 */
                                b.startDeclaration(type(Object.class), "interpreterState");
                                b.string("frame == ", localFrame(), " ? null : ", localFrame());
                                b.end();
                            }

                            b.startAssign("Object osrResult");
                            b.startStaticCall(types.BytecodeOSRNode, "tryOSR");
                            b.string("this");
                            b.string("(sp << 16) | " + readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.BYTECODE_INDEX))); // target

                            if (model.enableYield) {
                                b.string("interpreterState");
                            } else {
                                b.string("null"); // interpreterState
                            }
                            b.string("null"); // beforeTransfer
                            b.string("frame"); // parentFrame
                            b.end(2);

                            b.startIf().string("osrResult != null").end().startBlock();
                            /**
                             * executeOSR invokes BytecodeNode#continueAt, which returns an int
                             * encoding the sp and bci when it returns/when the bytecode is
                             * rewritten. Returning this value is correct in either case: If it's a
                             * return, we'll read the result out of the frame (the OSR code copies
                             * the OSR frame contents back into our frame first); if it's a rewrite,
                             * we'll transition and continue executing.
                             */
                            b.startReturn().cast(type(int.class)).string("osrResult").end();
                            b.end();

                            b.end();
                        }
                        b.statement("bci = + " + readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.BYTECODE_INDEX)));
                        b.statement("continue loop");
                        break;
                    case BRANCH_FALSE:
                        String booleanValue = "(Boolean) " + getFrameObject("sp - 1") + " == Boolean.TRUE";
                        b.startIf();
                        if (tier.isUncached()) {
                            b.string(booleanValue);
                        } else {
                            b.startStaticCall(types.BytecodeSupport, "profileBranch");
                            b.string("branchProfiles");
                            b.tree(readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.BRANCH_PROFILE)));
                            if (model.isBoxingEliminated(context.getType(boolean.class))) {
                                if (instr.isQuickening()) {
                                    b.startCall(lookupDoBranch(instr).getSimpleName().toString());
                                    if (model.specializationDebugListener) {
                                        b.string("this");
                                    }
                                    b.string("frame").string("bc").string("bci").string("sp");
                                    b.end();
                                } else {
                                    b.startCall(lookupDoSpecializeBranch(instr).getSimpleName().toString());
                                    if (model.specializationDebugListener) {
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
                        b.statement("continue loop");
                        b.end().startElseBlock();
                        b.statement("sp -= 1");
                        b.statement("bci = " + readImmediate("bc", "bci", instr.getImmediate("branch_target")));
                        b.statement("continue loop");
                        b.end();
                        break;
                    case CUSTOM_SHORT_CIRCUIT:
                        ShortCircuitInstructionModel shortCircuitInstruction = instr.shortCircuitModel;

                        b.startIf();

                        if (shortCircuitInstruction.continueWhen()) {
                            b.string("!");
                        }
                        /*
                         * NB: Short circuit operations can evaluate to an operand or to the boolean
                         * conversion of an operand. The stack is different in either case.
                         */
                        b.string("(boolean) ").string(getFrameObject("sp - 1"));

                        b.end().startBlock();
                        if (shortCircuitInstruction.returnConvertedBoolean()) {
                            // Stack: [..., convertedValue]
                            // leave convertedValue on the top of stack
                        } else {
                            // Stack: [..., value, convertedValue]
                            // pop convertedValue
                            b.statement(clearFrame("frame", "sp - 1"));
                            b.statement("sp -= 1");
                        }
                        b.statement("bci = " + readBc("bci + 1"));
                        b.statement("continue loop");
                        b.end().startElseBlock();
                        if (shortCircuitInstruction.returnConvertedBoolean()) {
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
                        b.statement("continue loop");
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
                            if (model.specializationDebugListener) {
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
                            if (model.specializationDebugListener) {
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
                            TypeMirror returnType = instr.signature.returnType;
                            b.startTryBlock();
                            b.startStatement();
                            startSetFrame(b, returnType).string("frame").string("sp");
                            b.startGroup();
                            b.startStaticCall(lookupExpectMethod(context.getType(Object.class), returnType));
                            b.string(localFrame() + ".getArguments()[" + readBc("bci + 1") + "]");
                            b.end(); // expect
                            b.end(); // argument group
                            b.end(); // set frame
                            b.end(); // statement
                            b.end().startCatchBlock(types.UnexpectedResultException, "e"); // try
                            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                            emitQuickening(b, "this", "bc", "bci", null,
                                            b.create().tree(createInstructionConstant(instr.getQuickeningRoot())).build());
                            b.startStatement();
                            startSetFrame(b, context.getType(Object.class)).string("frame").string("sp");
                            b.string("e.getResult()");
                            b.end(); // set frame
                            b.end(); // statement
                            b.end(); // catch block
                        } else {
                            b.startStatement();
                            startSetFrame(b, context.getType(Object.class)).string("frame").string("sp");
                            b.startGroup();
                            b.string(localFrame() + ".getArguments()[" + readBc("bci + 1") + "]");
                            b.end(); // argument group
                            b.end(); // set frame
                            b.end(); // statement
                        }

                        b.statement("sp += 1");
                        break;
                    case LOAD_CONSTANT:
                        TypeMirror returnType = instr.signature.returnType;
                        if (tier.isUncached() || (model.usesBoxingElimination() && !ElementUtils.isObject(returnType))) {
                            b.startStatement();
                            startSetFrame(b, returnType).string("frame").string("sp");
                            b.startGroup();
                            b.cast(returnType);
                            b.string(readConst(readBc("bci + 1")));
                            b.end();
                            b.end();
                            b.end();
                        } else {
                            b.startIf().startStaticCall(types.CompilerDirectives, "inCompiledCode").end(2).startBlock();
                            b.statement("loadConstantCompiled(frame, bc, bci, sp, constants)");
                            b.end().startElseBlock();
                            b.statement(setFrameObject("sp", readConst(readBc("bci + 1"))));
                            b.end();
                        }
                        b.statement("sp += 1");
                        break;
                    case POP:
                        if (instr.isQuickening() || tier.isUncached() || !model.usesBoxingElimination()) {
                            b.startStatement();
                            b.startCall(lookupDoPop(instr).getSimpleName().toString());
                            if (model.specializationDebugListener) {
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
                            if (model.specializationDebugListener) {
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
                        emitReturnTopOfStack(b);
                        break;
                    case LOAD_LOCAL:
                        if (instr.isQuickening() || tier.isUncached() || !model.usesBoxingElimination()) {
                            b.startStatement();
                            b.startCall(lookupDoLoadLocal(instr).getSimpleName().toString());
                            if (model.specializationDebugListener) {
                                b.string("this");
                            }
                            b.string("frame");
                            if (model.enableYield) {
                                b.string("localFrame");
                            }
                            b.string("bc").string("bci").string("sp");
                            b.end();
                            b.end();
                        } else {
                            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                            b.startStatement();
                            b.startCall(lookupDoSpecializeLoadLocal(instr).getSimpleName().toString());
                            if (model.specializationDebugListener) {
                                b.string("this");
                            }
                            b.string("frame");
                            if (model.enableYield) {
                                b.string("localFrame");
                            }
                            b.string("bc").string("bci").string("sp");
                            b.end();
                            b.end();
                        }
                        b.statement("sp += 1");
                        break;
                    case LOAD_LOCAL_MATERIALIZED:
                        String materializedFrame = "((VirtualFrame) " + getFrameObject("sp - 1)");
                        if (instr.isQuickening() || tier.isUncached() || !model.usesBoxingElimination()) {
                            b.startStatement();
                            b.startCall(lookupDoLoadLocal(instr).getSimpleName().toString());
                            if (model.specializationDebugListener) {
                                b.string("this");
                            }
                            b.string("frame").string(materializedFrame).string("bc").string("bci").string("sp");
                            b.end();
                            b.end();
                        } else {
                            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                            b.startStatement();
                            b.startCall(lookupDoSpecializeLoadLocal(instr).getSimpleName().toString());
                            if (model.specializationDebugListener) {
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
                            startGetFrameUnsafe(b, "frame", type(Object.class)).string("sp - 1").end();
                            b.end();
                            b.end();
                        }
                        b.statement(clearFrame("frame", "sp - 1"));
                        b.statement("sp -= 1");
                        break;
                    case STORE_LOCAL_MATERIALIZED:
                        materializedFrame = "((VirtualFrame) " + getFrameObject("sp - 2)");

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
                            startGetFrameUnsafe(b, localFrame(), type(Object.class)).string("sp - 1").end();
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
                            if (model.specializationDebugListener) {
                                b.string("this");
                            }
                            b.string("frame").string("bc").string("bci").string("sp");
                            b.end();
                            b.end();
                        } else {
                            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                            b.startStatement();
                            b.startCall(lookupDoSpecializeMergeConditional(instr).getSimpleName().toString());
                            if (model.specializationDebugListener) {
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
                        b.statement("throw sneakyThrow((Throwable) " + getFrameObject(localFrame(), readBc("bci + 1")) + ")");
                        break;
                    case YIELD:
                        storeBciInFrameIfNecessary(b);
                        emitBeforeReturnProfiling(b);
                        b.statement("int numLocals = $root.numLocals");
                        b.statement(copyFrameTo("frame", "numLocals", "localFrame", "numLocals", "(sp - 1 - numLocals)"));

                        b.startDeclaration(continuationRootNodeImpl.asType(), "continuationRootNode");
                        b.cast(continuationRootNodeImpl.asType());
                        b.string(readConst(readBc("bci + 1")));
                        b.end();

                        b.startDeclaration(types.ContinuationResult, "continuationResult");
                        b.startCall("continuationRootNode.createContinuation");
                        b.string(localFrame());
                        b.string(getFrameObject("sp - 1"));
                        b.end(2);

                        b.statement(setFrameObject("sp - 1", "continuationResult"));
                        emitReturnTopOfStack(b);
                        break;
                    case STORE_NULL:
                        b.statement(setFrameObject("sp", "null"));
                        b.statement("sp += 1");
                        break;
                    case CLEAR_LOCAL:
                        b.statement(clearFrame("frame", readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.LOCAL_OFFSET)).toString()));
                        break;
                    case LOAD_VARIADIC:
                        int effect = -instr.variadicPopCount + 1;
                        b.startStatement();
                        if (instr.variadicPopCount == 0) {
                            b.string(setFrameObject("sp", emptyObjectArray.getSimpleName().toString()));
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
                        b.statement(setFrameObject("sp - 1", "mergeVariadic((Object[]) " + getFrameObject("sp - 1") + ")"));
                        break;
                    case INVALIDATE:
                        emitInvalidate(b);
                        break;
                    case CUSTOM:
                        results.add(buildCustomInstructionExecute(b, instr));
                        break;
                    case SUPERINSTRUCTION:
                        // not implemented yet
                        break;
                    default:
                        throw new UnsupportedOperationException("not implemented: " + instr.kind);
                }
                if (!instr.isControlFlow() && instr.kind != InstructionKind.INVALIDATE) {
                    b.statement("bci += " + instr.getInstructionLength());
                    b.statement("continue loop");
                }
                b.end();
            }

            b.end(); // switch

            b.end(); // try

            b.startCatchBlock(type(Throwable.class), "originalThrowable");
            storeBciInFrameIfNecessary(b);
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
                b.declaration(type(Throwable.class), "throwable", "originalThrowable");
                b.startIf().string("throwable instanceof ").type(types.ControlFlowException).string(" cfe").end().startBlock();
                b.startTryBlock();
                if (tier.isUncached()) {
                    b.statement("return resolveControlFlowException($root, " + localFrame() + ", bci, cfe, loopCounter, uncachedExecuteCount)");
                } else {
                    b.statement("return resolveControlFlowException($root, " + localFrame() + ", bci, cfe, loopCounter)");
                }
                b.end().startCatchBlock(types.ControlFlowException, "rethrownCfe");
                b.startThrow().string("rethrownCfe").end();
                b.end().startCatchBlock(types.AbstractTruffleException, "t");
                b.statement("throwable = t");
                b.end().startCatchBlock(type(Throwable.class), "t");
                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                b.statement("throwable = t");
                b.end();
                b.end(); // if
                b.declaration(type(Throwable.class), "ex", "resolveThrowable($root, " + localFrame() + ", bci, throwable)");
            } else {
                b.declaration(type(Throwable.class), "ex", "resolveThrowable($root, " + localFrame() + ", bci, originalThrowable)");
            }
            b.declaration(type(int.class), "handler", "-5");
            b.statement("int[] localHandlers = this.handlers");
            b.startWhile().string("(handler = resolveHandler(bci, handler + 5, localHandlers)) != -1").end().startBlock();

            b.statement("int local = localHandlers[handler + 4]");
            b.statement("int targetSp");
            boolean hasSpecialHandler = model.enableTagInstrumentation || model.epilogExceptional != null;

            if (hasSpecialHandler) {
                b.startTryBlock();
                b.startSwitch().string("local").end().startBlock();
                if (model.epilogExceptional != null) {
                    b.startCase().string("HANDLER_EPILOG_EXCEPTIONAL").end().startCaseBlock();
                    b.startIf().string("ex instanceof ").type(type(ThreadDeath.class)).end().startBlock();
                    b.statement("continue");
                    b.end();
                    b.startStatement().startCall("doEpilogExceptional");
                    b.string("$root").string("frame");
                    if (model.enableYield) {
                        b.string("localFrame");
                    }
                    b.string("bc").string("bci").string("sp");
                    b.startGroup().cast(types.AbstractTruffleException);
                    b.string("ex");
                    b.end();
                    b.string("localHandlers[handler + 2]");
                    b.end().end();
                    b.statement("throw sneakyThrow(ex)");
                    b.end();
                }
                if (model.enableTagInstrumentation) {
                    b.startCase().string("HANDLER_TAG_EXCEPTIONAL").end().startCaseBlock();
                    b.statement("int result = doTagExceptional($root, frame, bc, bci, ex, localHandlers[handler + 2], localHandlers[handler + 3])");
                    b.statement("targetSp = result >> 16 & 0xFFFF");
                    b.statement("bci = result & 0xFFFF");
                    b.startIf().string("sp < targetSp + $root.numLocals").end().startBlock();
                    b.lineComment("The instrumentation pushed a value on the stack.");
                    b.statement("assert sp == targetSp + $root.numLocals - 1");
                    b.statement("sp++");
                    b.end();
                    b.statement("break");
                    b.end();
                }

                b.caseDefault().startCaseBlock();
            }
            b.startIf().string("ex instanceof ").type(type(ThreadDeath.class)).end().startBlock();
            b.statement("continue");
            b.end();
            b.startAssert().string("ex instanceof ").type(types.AbstractTruffleException).end();
            b.statement("bci = localHandlers[handler + 2]");
            b.statement("targetSp = localHandlers[handler + 3]");
            b.statement(setFrameObject(localFrame(), "localHandlers[handler + 4]", "ex"));

            if (hasSpecialHandler) {
                b.statement("break");
                b.end(); // case block
                b.end(); // switch
                b.end(); // try
                b.startCatchBlock(type(Throwable.class), "t");
                b.startIf().string("t != ex").end().startBlock();
                b.statement("ex = resolveThrowable($root, " + localFrame() + ", bci, t)");
                b.end();
                b.statement("continue");
                b.end();
            }

            b.statement("int handlerSp = targetSp + $root.numLocals");
            b.statement("assert sp >= handlerSp");
            b.startWhile().string("sp > handlerSp").end().startBlock();
            b.statement(clearFrame("frame", "--sp"));
            b.end();
            b.statement("continue loop");

            b.end(); // while

            /**
             * NB: Reporting here ensures loop counts are reported before a guest-language exception
             * bubbles up. Loop counts may be lost when host exceptions are thrown (a compromise to
             * avoid complicating the generated code too much).
             */
            emitBeforeReturnProfiling(b);
            b.statement("throw sneakyThrow(ex)");

            b.end(); // catch

            b.end(); // while (true)

            if (model.enableTracing) {
                b.end().startFinallyBlock();
                b.statement("tracer.endRoot(this)");
                b.end();
            }

            if (tier.isUncached()) {
                b.end().startFinallyBlock();
                b.startStatement();
                b.startCall("encapsulatingNode", "set").string("prev").end();
                b.end();
                b.end();
            }

            results.addAll(doInstructionMethods.values());
            return results;
        }

        private static boolean isInstructionReachable(InstructionModel model) {
            return !model.isEpilogExceptional();
        }

        private void emitInvalidate(CodeTreeBuilder b) {
            if (tier.isCached()) {
                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
            }
            b.startReturn().string("(sp << 16) | bci").end();
        }

        private CodeExecutableElement createResolveControlFlowException() {
            CodeExecutableElement method = new CodeExecutableElement(
                            Set.of(PRIVATE),
                            type(int.class), "resolveControlFlowException",
                            new CodeVariableElement(bytecodeNodeGen.asType(), "$root"),
                            new CodeVariableElement(types.VirtualFrame, "frame"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(types.ControlFlowException, "cfe"),
                            new CodeVariableElement(loopCounter.asType(), "loopCounter"));
            if (tier.isUncached()) {
                method.addParameter(new CodeVariableElement(type(int.class), "uncachedExecuteCount"));
            }

            method.getThrownTypes().add(type(Throwable.class));

            method.addAnnotationMirror(new CodeAnnotationMirror(types.HostCompilerDirectives_InliningCutoff));
            CodeTreeBuilder b = method.createBuilder();

            b.startAssign("Object result").startCall("$root", model.interceptControlFlowException).string("cfe").string("frame").string("this").string("bci").end(2);
            emitBeforeReturnProfiling(b);
            // There may not be room above the sp. Just use the first stack slot.
            b.statement(setFrameObject("$root.numLocals", "result"));
            b.startDeclaration(type(int.class), "sp").string("$root.numLocals + 1").end();
            emitReturnTopOfStack(b);
            return method;

        }

        private CodeExecutableElement createResolveThrowable() {
            CodeExecutableElement method = new CodeExecutableElement(
                            Set.of(PRIVATE),
                            type(Throwable.class), "resolveThrowable",
                            new CodeVariableElement(bytecodeNodeGen.asType(), "$root"),
                            new CodeVariableElement(types.VirtualFrame, "frame"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(Throwable.class), "throwable"));

            method.addAnnotationMirror(new CodeAnnotationMirror(types.HostCompilerDirectives_InliningCutoff));
            mergeSuppressWarnings(method, "static-method");

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
                    b.startAssign("throwable").startCall("$root", model.interceptInternalException).string("throwable").string("this").string("bci").end(2);
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
            method.addAnnotationMirror(new CodeAnnotationMirror(types.HostCompilerDirectives_InliningCutoff));
            method.addAnnotationMirror(new CodeAnnotationMirror(types.ExplodeLoop));

            if (!tier.isCached()) {
                method.getModifiers().add(STATIC);
            }

            CodeTreeBuilder b = method.createBuilder();

            if (tier.isCached()) {
                b.declaration(type(int.class), "handlerId", "Math.floorDiv(handler, 5)");
            }
            if (tier.isCached()) {
                b.startFor().string("int i = handler; i < localHandlers.length; i += 5, handlerId++").end().startBlock();
            } else {
                b.startFor().string("int i = handler; i < localHandlers.length; i += 5").end().startBlock();
            }
            b.startIf().string("localHandlers[i] > bci").end().startBlock().statement("continue").end();
            b.startIf().string("localHandlers[i + 1] <= bci").end().startBlock().statement("continue").end();

            if (tier.isCached()) {
                b.startIf().string("!this.exceptionProfiles_[handlerId]").end().startBlock();
                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                b.statement("this.exceptionProfiles_[handlerId] = true");
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
            }).collect(Collectors.groupingBy((i -> {
                return i.getImmediates();
            }))).values();
        }

        private CodeExecutableElement createDoEpilogExceptional() {
            CodeExecutableElement method = new CodeExecutableElement(
                            Set.of(PRIVATE),
                            type(void.class), "doEpilogExceptional");

            method.addParameter(new CodeVariableElement(bytecodeNodeGen.asType(), "$root"));
            method.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));
            if (model.enableYield) {
                method.addParameter(new CodeVariableElement(types.VirtualFrame, "localFrame"));
            }
            method.addParameter(new CodeVariableElement(type(short[].class), "bc"));
            method.addParameter(new CodeVariableElement(type(int.class), "bci"));
            method.addParameter(new CodeVariableElement(type(int.class), "sp"));
            method.addParameter(new CodeVariableElement(types.AbstractTruffleException, "exception"));
            method.addParameter(new CodeVariableElement(type(int.class), "nodeId"));

            InstructionModel instr = model.epilogExceptional.operation.instruction;

            CodeTreeBuilder b = method.createBuilder();
            TypeMirror cachedType = getCachedDataClassType(instr);
            if (tier.isCached()) {
                b.declaration(cachedType, "node", "this.epilogExceptionalNode_");
            }

            List<CodeVariableElement> extraParams = createExtraParameters();
            buildCallExecute(b, model.epilogExceptional.operation.instruction, "exception", extraParams);
            return method;

        }

        private CodeExecutableElement createDoTagExceptional() {
            CodeExecutableElement method = new CodeExecutableElement(
                            Set.of(PRIVATE),
                            type(int.class), "doTagExceptional",
                            new CodeVariableElement(bytecodeNodeGen.asType(), "$root"),
                            new CodeVariableElement(types.VirtualFrame, "frame"),
                            new CodeVariableElement(type(short[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(Throwable.class), "exception"),
                            new CodeVariableElement(type(int.class), "nodeId"),
                            new CodeVariableElement(type(int.class), "handlerSp"));

            method.getThrownTypes().add(type(Throwable.class));

            Collection<List<InstructionModel>> groupedInstructions = groupInstructionsByKindAndImmediates(InstructionKind.TAG_LEAVE, InstructionKind.TAG_LEAVE_VOID);

            CodeTreeBuilder b = method.createBuilder();
            b.declaration(type(boolean.class), "wasOnReturnExecuted");
            b.declaration(type(int.class), "nextBci");
            b.declaration(type(int.class), "nextSp");

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

            b.declaration(tagNode.asType(), "node", "this.tagRoot.tagNodes[nodeId]");
            b.statement("Object result = node.findProbe().onReturnExceptionalOrUnwind(frame, exception, wasOnReturnExecuted)");
            b.startIf().string("result == null").end().startBlock();
            b.startThrow().string("exception").end();
            b.end();
            b.startElseIf().string("result == ").staticReference(types.ProbeNode, "UNWIND_ACTION_REENTER").end().startBlock();
            b.lineComment("Reenter by jumping to the begin bci.");
            b.statement("return (handlerSp << 16) | node.enterBci");
            b.end().startElseBlock();
            b.lineComment("We jump to the return adress which is at sp + 1.");

            b.declaration(type(int.class), "targetSp");
            b.declaration(type(int.class), "targetBci");

            b.startSwitch().string("readValidBytecode(bc, node.leaveBci)").end().startBlock();
            for (var entry : model.getInstructions().stream().filter((i) -> i.kind == InstructionKind.TAG_LEAVE).collect(Collectors.groupingBy((i) -> {
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
                b.statement("targetBci = node.leaveBci + " + length);
                b.statement("targetSp = handlerSp + 1 ");

                CodeExecutableElement expectMethod = null;
                if (!ElementUtils.isObject(targetType)) {
                    expectMethod = lookupExpectMethod(parserType, targetType);
                    b.startTryBlock();
                }

                b.startStatement();
                startSetFrame(b, targetType).string("frame").string("targetSp - 1 + $root.numLocals");
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
                    startSetFrame(b, type(Object.class)).string("frame").string("targetSp - 1 + $root.numLocals").string("e.getResult()").end();
                    b.end(); // statement
                    b.end(); // catch
                }

                b.statement("break");
                b.end();
            }
            for (InstructionModel instruction : model.getInstructions().stream().filter((i) -> i.kind == InstructionKind.TAG_LEAVE_VOID).toList()) {
                b.startCase().tree(createInstructionConstant(instruction)).end();
                b.startCaseBlock();
                b.statement("targetBci = node.leaveBci + " + instruction.getInstructionLength());
                b.statement("targetSp = handlerSp ");
                b.lineComment("discard return value");
                b.statement("break");
                b.end();
            }
            b.caseDefault().startCaseBlock();
            b.tree(GeneratorUtils.createShouldNotReachHere());
            b.end(); // case default
            b.end(); // switch

            b.startAssert().string("targetBci < bc.length : ").doubleQuote("leaveBci must be reachable").end();
            b.statement("return ((targetSp) << 16) | targetBci");
            b.end();

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
                            new CodeVariableElement(type(short[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(int.class), "sp"));

            method.addAnnotationMirror(new CodeAnnotationMirror(types.HostCompilerDirectives_InliningCutoff));

            CodeTreeBuilder b = method.createBuilder();
            InstructionImmediate imm = instr.getImmediate(ImmediateKind.TAG_NODE);
            b.startDeclaration(tagNode.asType(), "tagNode");
            b.tree(readTagNode(tagNode.asType(), CodeTreeBuilder.singleString(readBc("bci + " + imm.offset()))));
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
                            new CodeVariableElement(type(short[].class), "bc"),
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
            b.tree(readTagNode(tagNode.asType(), CodeTreeBuilder.singleString(readBc("bci + " + imm.offset()))));
            b.end();
            b.statement("tagNode.findProbe().onYield(frame, returnValue)");

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
                            new CodeVariableElement(type(short[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(int.class), "sp"));

            method.addAnnotationMirror(new CodeAnnotationMirror(types.HostCompilerDirectives_InliningCutoff));

            CodeTreeBuilder b = method.createBuilder();
            InstructionImmediate imm = instr.getImmediate(ImmediateKind.TAG_NODE);
            b.startDeclaration(tagNode.asType(), "tagNode");
            b.tree(readTagNode(tagNode.asType(), CodeTreeBuilder.singleString(readBc("bci + " + imm.offset()))));
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
                            new CodeVariableElement(type(short[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(int.class), "sp"));

            method.addAnnotationMirror(new CodeAnnotationMirror(types.HostCompilerDirectives_InliningCutoff));

            CodeTreeBuilder b = method.createBuilder();
            InstructionImmediate imm = instr.getImmediate(ImmediateKind.TAG_NODE);
            b.startDeclaration(tagNode.asType(), "tagNode");
            b.tree(readTagNode(tagNode.asType(), CodeTreeBuilder.singleString(readBc("bci + " + imm.offset()))));
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
                            new CodeVariableElement(type(short[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(int.class), "sp"));

            boolean isMaterialized = instr.kind == InstructionKind.LOAD_LOCAL_MATERIALIZED;
            if (isMaterialized) {
                method.getParameters().add(0, new CodeVariableElement(types.Frame, "stackFrame"));
            }

            if (model.specializationDebugListener) {
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
                if (model.specializationDebugListener) {
                    b.string("$this");
                }
                b.string("frame").string("bc").string("bci").string("sp");
                b.end().end();
            }

            b.end();

            InstructionImmediate imm = instr.getImmediate(ImmediateKind.TAG_NODE);
            b.startDeclaration(tagNode.asType(), "tagNode");
            b.tree(readTagNode(tagNode.asType(), CodeTreeBuilder.singleString(readBc("bci + " + imm.offset()))));
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
                            new CodeVariableElement(type(short[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(int.class), "sp"));

            if (model.specializationDebugListener) {
                method.getParameters().add(0, new CodeVariableElement(type.getSuperclass(), "$this"));
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
            b.tree(readTagNode(tagNode.asType(), CodeTreeBuilder.singleString(readBc("bci + " + imm.offset()))));
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
                            new CodeVariableElement(type(short[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(int.class), "sp"));

            boolean isMaterialized = instr.kind == InstructionKind.LOAD_LOCAL_MATERIALIZED;
            if (isMaterialized) {
                method.getParameters().add(0, new CodeVariableElement(types.Frame, "stackFrame"));
            }

            if (model.specializationDebugListener) {
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

                b.startIf().string("frame.getTag(sp - 1) != ").staticReference(frameSlotKindConstant.get(inputType)).end().startBlock();
                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                b.startStatement().startCall(lookupDoSpecializeBranch(instr.getQuickeningRoot()).getSimpleName().toString());
                if (model.specializationDebugListener) {
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
                            new CodeVariableElement(type(short[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(int.class), "sp"));

            if (model.specializationDebugListener) {
                method.getParameters().add(0, new CodeVariableElement(type.getSuperclass(), "$this"));
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
                            new CodeVariableElement(type(short[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(int.class), "sp"));

            boolean isMaterialized = instr.kind == InstructionKind.LOAD_LOCAL_MATERIALIZED;
            if (isMaterialized) {
                method.getParameters().add(0, new CodeVariableElement(types.Frame, "stackFrame"));
            }

            if (model.specializationDebugListener) {
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
            if (model.specializationDebugListener) {
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
                            new CodeVariableElement(type(short[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(int.class), "sp"));

            if (model.specializationDebugListener) {
                method.getParameters().add(0, new CodeVariableElement(abstractBytecodeNode.asType(), "$this"));
            }

            TypeMirror boxingType = context.getType(boolean.class);

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
            startRequireFrame(b, context.getType(Object.class));
            b.string("frame").string("sp - 1");
            b.end();
            b.end(); // statement

            b.declaration(type(short.class), "newInstruction");
            b.declaration(type(short.class), "newOperand");
            b.declaration(type(int.class), "operandIndex", "ACCESS.shortArrayRead(bc, bci + 3)");
            b.declaration(type(short.class), "operand", "ACCESS.shortArrayRead(bc, operandIndex)");

            b.startIf().string("(newOperand = ").startCall(createApplyQuickeningName(boxingType)).string("operand").end().string(") != -1").end().startBlock();
            b.startStatement().string("newInstruction = ").tree(createInstructionConstant(unboxedInstruction)).end();
            emitOnSpecialize(b, "$this", "bci", "bc[bci]", "BranchFalse$" + unboxedInstruction.getQuickeningName());
            b.end().startElseBlock();
            b.startStatement().string("newInstruction = ").tree(createInstructionConstant(boxedInstruction)).end();
            b.startStatement().string("newOperand = operand").end();
            emitOnSpecialize(b, "$this", "bci", "bc[bci]", "BranchFalse$" + boxedInstruction.getQuickeningName());
            b.end(); // else block

            emitQuickeningOperand(b, "$this", "bc", "bci", null, 0, "operandIndex", "operand", "newOperand");
            emitQuickening(b, "$this", "bc", "bci", null, "newInstruction");

            b.startReturn().string("value").end();

            doInstructionMethods.put(instr, method);
            return method;
        }

        private CodeExecutableElement lookupDoLoadLocal(InstructionModel instr) {
            CodeExecutableElement method = doInstructionMethods.get(instr);
            if (method != null) {
                return method;
            }

            method = new CodeExecutableElement(
                            Set.of(PRIVATE, STATIC),
                            type(void.class), instructionMethodName(instr),
                            new CodeVariableElement(types.Frame, "frame"),
                            new CodeVariableElement(type(short[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(int.class), "sp"));

            boolean needsStackFrame = instr.kind == InstructionKind.LOAD_LOCAL_MATERIALIZED || model.enableYield;
            if (needsStackFrame) {
                method.getParameters().add(0, new CodeVariableElement(types.Frame, "stackFrame"));
            }

            if (model.specializationDebugListener) {
                method.getParameters().add(0, new CodeVariableElement(abstractBytecodeNode.asType(), "$this"));
            }

            final TypeMirror inputType = instr.signature.returnType;
            final TypeMirror slotType = instr.specializedType != null ? instr.specializedType : type(Object.class);

            CodeTreeBuilder b = method.createBuilder();

            CodeTree readSlot = readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.LOCAL_OFFSET));
            boolean generic = ElementUtils.typeEquals(type(Object.class), slotType);

            if (!generic) {
                b.startTryBlock();
            }

            b.startStatement();
            startSetFrame(b, inputType).string(needsStackFrame ? "stackFrame" : "frame");
            if (instr.kind == InstructionKind.LOAD_LOCAL_MATERIALIZED) {
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
                if (model.enableLocalScoping) {
                    method.getModifiers().remove(Modifier.STATIC);
                }

                b.end().startCatchBlock(types.UnexpectedResultException, "ex");
                b.startStatement().startCall(lookupDoSpecializeLoadLocal(instr.getQuickeningRoot()).getSimpleName().toString());
                if (model.specializationDebugListener) {
                    b.string("$this");
                }
                if (needsStackFrame) {
                    b.string("stackFrame");
                }
                b.string("frame").string("bc").string("bci").string("sp");
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
                            Set.of(PRIVATE, STATIC),
                            type(void.class), instructionMethodName(instr),
                            new CodeVariableElement(types.Frame, "frame"),
                            new CodeVariableElement(type(short[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(int.class), "sp"));

            boolean needsStackFrame = instr.kind == InstructionKind.LOAD_LOCAL_MATERIALIZED || model.enableYield;
            if (needsStackFrame) {
                method.getParameters().add(0, new CodeVariableElement(types.Frame, "stackFrame"));
            }

            if (model.specializationDebugListener) {
                method.getParameters().add(0, new CodeVariableElement(abstractBytecodeNode.asType(), "$this"));
            }

            CodeTreeBuilder b = method.createBuilder();

            b.declaration(type(short.class), "newInstruction");
            b.declaration(type(int.class), "slot", readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.LOCAL_OFFSET)));

            b.declaration(type(Object.class), "value");

            Map<TypeMirror, InstructionModel> typeToSpecialization = new HashMap<>();
            List<InstructionModel> specializations = instr.quickenedInstructions;
            for (InstructionModel specialization : specializations) {
                if (specialization.specializedType != null) {
                    typeToSpecialization.put(specialization.specializedType, specialization);
                } else {
                    typeToSpecialization.put(context.getType(Object.class), specialization);
                }
            }

            String stackFrame = needsStackFrame ? "stackFrame" : "frame";

            InstructionModel genericInstruction = typeToSpecialization.get(type(Object.class));

            if (model.enableLocalScoping) {
                method.getModifiers().remove(Modifier.STATIC);
                b.declaration(type(byte[].class), "localTags", "this.localTags_");
                b.declaration(type(int.class), "localIndex", readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.LOCAL_INDEX)));
                b.declaration(types.FrameSlotKind, "kind", "FrameSlotKind.fromTag(localTags[localIndex])");
            } else {
                b.declaration(types.FrameSlotKind, "kind", "frame.getFrameDescriptor().getSlotKind(slot)");
            }

            b.startTryBlock();

            b.startSwitch().string("kind").end().startBlock();
            for (TypeMirror boxingType : model.boxingEliminatedTypes) {
                InstructionModel boxedInstruction = typeToSpecialization.get(boxingType);

                b.startCase().string(ElementUtils.firstLetterUpperCase(ElementUtils.getSimpleName(boxingType))).end();
                b.startCaseBlock();
                b.startStatement().string("newInstruction = ").tree(createInstructionConstant(boxedInstruction)).end();
                emitOnSpecialize(b, "$this", "bci", "bc[bci]", "LoadLocal$" + boxedInstruction.getQuickeningName());
                b.startStatement();
                b.string("value = ");
                startExpectFrameUnsafe(b, "frame", boxingType).string("slot").end();
                b.end();
                b.statement("break");
                b.end();
            }

            b.startCase().string("Object").end();
            b.startCase().string("Illegal").end();
            b.startCaseBlock();
            b.startStatement().string("newInstruction = ").tree(createInstructionConstant(genericInstruction)).end();
            emitOnSpecialize(b, "$this", "bci", "bc[bci]", "LoadLocal$" + genericInstruction.getQuickeningName());
            b.startStatement();
            b.string("value = ");
            startExpectFrameUnsafe(b, "frame", type(Object.class)).string("slot").end();
            b.end();
            b.statement("break");
            b.end();

            b.caseDefault().startCaseBlock();
            b.tree(GeneratorUtils.createShouldNotReachHere("Unexpected FrameSlotKind."));
            b.end();

            b.end(); // switch

            b.end().startCatchBlock(types.UnexpectedResultException, "ex");

            // If a FrameSlotException occurs, specialize to the generic version.
            b.startStatement().string("newInstruction = ").tree(createInstructionConstant(genericInstruction)).end();
            emitOnSpecialize(b, "$this", "bci", "bc[bci]", "LoadLocal$" + genericInstruction.getQuickeningName());
            b.startStatement();
            b.string("value = ex.getResult()");
            b.end();

            b.end(); // catch

            emitQuickening(b, "$this", "bc", "bci", null, "newInstruction");
            b.startStatement();
            startSetFrame(b, type(Object.class)).string(stackFrame);
            if (instr.kind == InstructionKind.LOAD_LOCAL_MATERIALIZED) {
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
                            new CodeVariableElement(type(short[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(int.class), "sp"));

            if (model.specializationDebugListener) {
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
                if (model.specializationDebugListener) {
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
                            new CodeVariableElement(type(short[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(int.class), "sp"),
                            new CodeVariableElement(type(Object.class), "local"));

            if (model.specializationDebugListener) {
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
            b.declaration(type(short.class), "operand", "ACCESS.shortArrayRead(bc, operandIndex)");
            b.declaration(type(short.class), "otherOperand", "ACCESS.shortArrayRead(bc, otherOperandIndex)");

            Map<TypeMirror, InstructionModel> typeToSpecialization = new HashMap<>();
            List<InstructionModel> specializations = instr.quickenedInstructions;
            for (InstructionModel specialization : specializations) {
                if (specialization.specializedType != null) {
                    typeToSpecialization.put(specialization.specializedType, specialization);
                } else {
                    typeToSpecialization.put(context.getType(Object.class), specialization);
                }
            }
            InstructionModel genericInstruction = typeToSpecialization.get(type(Object.class));

            boolean elseIf = false;
            for (TypeMirror boxingType : model.boxingEliminatedTypes) {
                elseIf = b.startIf(elseIf);
                b.string("local").instanceOf(ElementUtils.boxType(boxingType));
                b.newLine().string("   && (");
                b.string("(newOperand = ").startCall(createApplyQuickeningName(boxingType)).string("operand").end().string(") != -1)");
                b.end().startBlock();

                InstructionModel boxedInstruction = typeToSpecialization.get(boxingType);
                InstructionModel unboxedInstruction = boxedInstruction.quickenedInstructions.get(0);
                b.startSwitch().string(readBc("bci")).end().startBlock();
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
                            new CodeVariableElement(type(short[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(int.class), "sp"));

            boolean materialized = instr.kind == InstructionKind.STORE_LOCAL_MATERIALIZED;
            boolean needsStackFrame = materialized || model.enableYield;
            if (needsStackFrame) {
                method.getParameters().add(0, new CodeVariableElement(types.Frame, "stackFrame"));
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
                b.end().end();

                b.returnDefault();
                b.end(); // catch block
            } else {
                b.startDeclaration(inputType, "local");
                startRequireFrame(b, inputType).string(stackFrame).string("sp - 1").end();
                b.end();
            }

            boolean generic = ElementUtils.typeEquals(type(Object.class), inputType);

            CodeTree readSlot = readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.LOCAL_OFFSET));
            if (generic && !ElementUtils.needsCastTo(inputType, slotType)) {
                b.startStatement();
                startSetFrame(b, slotType).string("frame").tree(readSlot);
                b.string("local");
                b.end();
                b.end();
                b.statement(clearFrame(stackFrame, "sp - 1"));
                if (instr.kind == InstructionKind.STORE_LOCAL_MATERIALIZED) {
                    b.statement(clearFrame(stackFrame, "sp - 2"));
                }
            } else {
                if (!model.usesBoxingElimination()) {
                    throw new AssertionError("Unexpected path.");
                }

                boolean needsCast = ElementUtils.needsCastTo(inputType, slotType);
                b.declaration(type(int.class), "slot", readSlot);

                if (model.enableLocalScoping) {
                    b.declaration(type(int.class), "localIndex", readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.LOCAL_INDEX)));
                }

                String bytecodeNode;
                if (materialized) {
                    b.startDeclaration(abstractBytecodeNode.asType(), "bytecodeNode");
                    b.startCall("getRoot().getBytecodeRootNodeImpl");
                    b.tree(readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.LOCAL_ROOT)));
                    b.end().string(".getBytecodeNodeImpl()");
                    b.end();
                    bytecodeNode = "bytecodeNode";
                } else {
                    bytecodeNode = "this";
                }
                b.startDeclaration(types.FrameSlotKind, "kind");
                b.startCall(bytecodeNode, "getCachedLocalKindInternal");
                b.string("frame").string("slot");
                if (model.enableLocalScoping) {
                    b.string("localIndex");
                }
                b.end(); // call
                b.end(); // declaration

                b.startIf().string("kind == ").staticReference(types.FrameSlotKind, ElementUtils.firstLetterUpperCase(ElementUtils.getSimpleName(slotType)));
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

                if (instr.kind == InstructionKind.STORE_LOCAL_MATERIALIZED) {
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
                            new CodeVariableElement(type(short[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(int.class), "sp"),
                            new CodeVariableElement(type(Object.class), "local"));

            boolean materialized = instr.kind == InstructionKind.STORE_LOCAL_MATERIALIZED;
            boolean needsStackFrame = materialized || model.enableYield;
            if (needsStackFrame) {
                method.getParameters().add(0, new CodeVariableElement(types.Frame, "stackFrame"));
            }

            String stackFrame = needsStackFrame ? "stackFrame" : "frame";

            CodeTreeBuilder b = method.createBuilder();

            b.declaration(type(short.class), "newInstruction");
            b.declaration(type(short.class), "newOperand");
            b.declaration(type(int.class), "operandIndex", readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.BYTECODE_INDEX)));
            b.declaration(type(short.class), "operand", "ACCESS.shortArrayRead(bc, operandIndex)");
            b.declaration(type(int.class), "slot", readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.LOCAL_OFFSET)));

            if (model.enableLocalScoping) {
                b.declaration(type(int.class), "localIndex", readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.LOCAL_INDEX)));
            }

            String bytecodeNode;
            if (materialized) {
                b.startDeclaration(abstractBytecodeNode.asType(), "bytecodeNode");
                b.startCall("this.getRoot().getBytecodeRootNodeImpl");
                b.tree(readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.LOCAL_ROOT)));
                b.end().string(".getBytecodeNodeImpl()");
                b.end();
                bytecodeNode = "bytecodeNode";
            } else {
                bytecodeNode = "this";
            }
            b.startDeclaration(types.FrameSlotKind, "oldKind");
            b.startCall(bytecodeNode, "getCachedLocalKindInternal");
            b.string("frame").string("slot");
            if (model.enableLocalScoping) {
                b.string("localIndex");
            }
            b.end(); // call
            b.end(); // declaration
            b.declaration(types.FrameSlotKind, "newKind");

            Map<TypeMirror, InstructionModel> typeToSpecialization = new HashMap<>();
            List<InstructionModel> specializations = instr.quickenedInstructions;
            for (InstructionModel specialization : specializations) {
                if (specialization.specializedType != null) {
                    typeToSpecialization.put(specialization.specializedType, specialization);
                } else {
                    typeToSpecialization.put(context.getType(Object.class), specialization);
                }
            }

            InstructionModel genericInstruction = typeToSpecialization.get(type(Object.class));

            boolean elseIf = false;
            for (TypeMirror boxingType : model.boxingEliminatedTypes) {
                elseIf = b.startIf(elseIf);
                b.string("local").instanceOf(ElementUtils.boxType(boxingType)).end().startBlock();

                InstructionModel boxedInstruction = typeToSpecialization.get(boxingType);
                InstructionModel unboxedInstruction = boxedInstruction.quickenedInstructions.get(0);

                b.startSwitch().string("oldKind").end().startBlock();

                String kindName = ElementUtils.firstLetterUpperCase(ElementUtils.getSimpleName(boxingType));
                b.startCase().string(kindName).end();
                b.startCase().string("Illegal").end();
                b.startCaseBlock();

                b.startIf().string("(newOperand = ").startCall(createApplyQuickeningName(boxingType)).string("operand").end().string(") != -1").end().startBlock();
                b.startStatement().string("newInstruction = ").tree(createInstructionConstant(unboxedInstruction)).end();
                b.end().startElseBlock();
                b.startStatement().string("newInstruction = ").tree(createInstructionConstant(boxedInstruction)).end();
                b.startStatement().string("newOperand = operand").end();
                b.end(); // else block
                emitOnSpecialize(b, "this", "bci", "bc[bci]", "StoreLocal$" + kindName);
                b.startStatement().string("newKind = ").staticReference(types.FrameSlotKind, kindName).end();
                b.startStatement();
                startSetFrame(b, boxingType).string("frame").string("slot").startGroup().cast(boxingType).string("local").end().end();
                b.end();
                b.statement("break");
                b.end();

                for (TypeMirror otherType : model.boxingEliminatedTypes) {
                    if (ElementUtils.typeEquals(otherType, boxingType)) {
                        continue;
                    }
                    b.startCase().string(ElementUtils.firstLetterUpperCase(ElementUtils.getSimpleName(otherType))).end();
                }

                b.startCase().string("Object").end();
                b.startCaseBlock();
                b.startStatement().string("newInstruction = ").tree(createInstructionConstant(genericInstruction)).end();
                b.startStatement().string("newOperand = ").startCall("undoQuickening").string("operand").end().end();
                b.startStatement().string("newKind = ").staticReference(types.FrameSlotKind, "Object").end();
                emitOnSpecialize(b, "this", "bci", "bc[bci]", "StoreLocal$" + genericInstruction.getQualifiedQuickeningName());
                b.startStatement();
                startSetFrame(b, type(Object.class)).string("frame").string("slot").string("local").end();
                b.end();
                b.statement("break");
                b.end();

                b.caseDefault().startCaseBlock();
                b.tree(GeneratorUtils.createShouldNotReachHere("Unexpected FrameSlotKind."));
                b.end();

                b.end(); // switch
                b.end(); // if block
            }

            b.startElseBlock(elseIf);
            b.startStatement().string("newInstruction = ").tree(createInstructionConstant(genericInstruction)).end();
            b.startStatement().string("newOperand = ").startCall("undoQuickening").string("operand").end().end();
            b.startStatement().string("newKind = ").staticReference(types.FrameSlotKind, "Object").end();
            emitOnSpecialize(b, "this", "bci", "bc[bci]", "StoreLocal$" + genericInstruction.getQualifiedQuickeningName());
            b.startStatement();
            startSetFrame(b, type(Object.class)).string("frame").string("slot").string("local").end();
            b.end();

            b.end();

            b.startStatement().startCall(bytecodeNode, "setCachedLocalKindInternal");
            b.string("slot");
            b.string("newKind");
            if (model.enableLocalScoping) {
                b.string("localIndex");
            }
            b.end().end();

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
         * We use this method to load constants on the compiled code path.
         *
         * The compiler can often detect and remove redundant box-unbox sequences, but when we load
         * primitives from the constants array that are already boxed, there is no initial "box"
         * operation. By extracting and re-boxing primitive values here, we create a fresh "box"
         * operation with which the compiler can match and eliminate subsequent "unbox" operations.
         */
        private CodeExecutableElement createLoadConstantCompiled() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC, FINAL), context.getType(void.class), "loadConstantCompiled");
            ex.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));
            ex.addParameter(new CodeVariableElement(context.getType(short[].class), "bc"));
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "bci"));
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "sp"));
            ex.addParameter(new CodeVariableElement(arrayOf(context.getDeclaredType(Object.class)), "constants"));

            CodeTreeBuilder b = ex.createBuilder();
            b.declaration(context.getDeclaredType(Object.class), "constant", readConst(readBc("bci + 1")));
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
            CodeExecutableElement ex = GeneratorUtils.overrideImplement((DeclaredType) abstractBytecodeNode.asType(), "adoptNodesAfterUpdate");
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("insert(this.cachedNodes_)");
            return ex;
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
        private CodeExecutableElement buildCustomInstructionExecute(CodeTreeBuilder continueAtBuilder, InstructionModel instr) {
            // To reduce bytecode in the dispatch loop, extract each implementation into a helper.
            String methodName = instructionMethodName(instr);
            CodeExecutableElement helper = new CodeExecutableElement(Set.of(PRIVATE, FINAL), context.getType(void.class), methodName);

            helper.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));
            if (model.enableYield) {
                helper.getParameters().add(new CodeVariableElement(types.VirtualFrame, "localFrame"));
            }

            if (!tier.isUncached()) {
                if (model.enableTracing) {
                    helper.addParameter(new CodeVariableElement(types.ExecutionTracer, "tracer"));
                }
            }

            /**
             * These additional parameters mirror the parameters declared in
             * {@link BytecodeDSLNodeGeneratorPlugs#additionalArguments()}. When one is updated, the
             * other should be kept in sync.
             */
            // we forward parameters with the same name when we call the helper, so save them here.
            List<CodeVariableElement> extraParams = createExtraParameters();

            if (tier.isCached()) {
                helper.getParameters().add(new CodeVariableElement(new ArrayCodeTypeMirror(types.Node), "cachedNodes"));
            }
            helper.getParameters().addAll(extraParams);

            boolean isVoid = instr.signature.isVoid;

            CodeTreeBuilder b = helper.createBuilder();

            // Since an instruction produces at most one value, stackEffect is at most 1.
            int stackEffect = (isVoid ? 0 : 1) - instr.signature.dynamicOperandCount;

            if (customInstructionMayReadBci(instr)) {
                storeBciInFrameIfNecessary(b);
            }

            TypeMirror cachedType = getCachedDataClassType(instr);

            if (tier.isCached()) {
                // If not in the uncached interpreter, we need to retrieve the node for the call.
                CodeTree nodeIndex = readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.NODE_PROFILE));
                CodeTree readNode = CodeTreeBuilder.createBuilder().tree(readNodeProfile(cachedType, nodeIndex)).build();
                b.declaration(cachedType, "node", readNode);

                if (model.enableTracing) {
                    b.startBlock();

                    b.startAssign("var specInfo").startStaticCall(types.Introspection, "getSpecializations");
                    b.string("node");
                    b.end(2);

                    b.startStatement().startCall("tracer.traceActiveSpecializations");
                    b.string("bci");
                    b.string(instr.getId());
                    b.startNewArray(arrayOf(context.getType(boolean.class)), null);
                    for (int i = 0; i < instr.nodeData.getSpecializations().size(); i++) {
                        if (instr.nodeData.getSpecializations().get(i).isFallback()) {
                            break;
                        }
                        b.string("specInfo.get(" + i + ").isActive()");
                    }
                    b.end();
                    b.end(2);

                    b.end();
                }

            }

            boolean unexpectedValue = hasUnexpectedExecuteValue(instr);
            if (unexpectedValue) {
                b.startTryBlock();
            }

            buildCallExecute(b, instr, null, extraParams);

            // Update the stack.
            if (!isVoid) {
                b.startStatement();
                if (instr.isReturnTypeQuickening()) {
                    startSetFrame(b, instr.signature.returnType);
                } else {
                    startSetFrame(b, context.getType(Object.class));
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
                if (!isVoid) {
                    b.startStatement();
                    startSetFrame(b, context.getType(Object.class));
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

            if (stackEffect > 0) {
                continueAtBuilder.statement("sp += " + stackEffect);
            } else if (stackEffect < 0) {
                continueAtBuilder.statement("sp -= " + -stackEffect);
            }

            return helper;
        }

        private GeneratedTypeMirror getCachedDataClassType(InstructionModel instr) {
            return new GeneratedTypeMirror("", cachedDataClassName(instr));
        }

        private List<CodeVariableElement> createExtraParameters() {
            List<CodeVariableElement> extraParams = new ArrayList<>();
            extraParams.addAll(List.of(
                            new CodeVariableElement(context.getType(short[].class), "bc"),
                            new CodeVariableElement(context.getType(int.class), "bci"),
                            new CodeVariableElement(context.getType(int.class), "sp")));
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

            // The uncached version takes all of its parameters. Other versions compute them.
            if (evaluatedArg != null) {
                b.string(evaluatedArg);
            } else if (tier.isUncached()) {
                for (int i = 0; i < instr.signature.constantOperandsBeforeCount; i++) {
                    TypeMirror constantOperandType = instr.operation.constantOperands.before().get(i).type();
                    b.startGroup();
                    if (!ElementUtils.isObject(constantOperandType)) {
                        b.cast(constantOperandType);
                    }
                    List<InstructionImmediate> imms = instr.getImmediates(ImmediateKind.CONSTANT);
                    InstructionImmediate imm = imms.get(i);
                    b.string(readConst(readBc("bci + " + imm.offset())));
                    b.end();
                }

                for (int i = 0; i < instr.signature.dynamicOperandCount; i++) {
                    TypeMirror targetType = instr.signature.getGenericType(i);
                    b.startGroup();
                    if (!ElementUtils.isObject(targetType)) {
                        b.cast(targetType);
                    }
                    b.string(getFrameObject("sp - " + (instr.signature.dynamicOperandCount - i)));
                    b.end();
                }

                for (int i = 0; i < instr.signature.constantOperandsAfterCount; i++) {
                    TypeMirror constantOperandType = instr.operation.constantOperands.after().get(i).type();
                    b.startGroup();
                    if (!ElementUtils.isObject(constantOperandType)) {
                        b.cast(constantOperandType);
                    }
                    List<InstructionImmediate> imms = instr.getImmediates(ImmediateKind.CONSTANT);
                    InstructionImmediate imm = imms.get(i);
                    b.string(readConst(readBc("bci + " + imm.offset())));
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
                b.statement("ACCESS.setInt(" + localFrame() + ", " + BCI_IDX + ", bci)");
            }
        }

        private static void emitReturnTopOfStack(CodeTreeBuilder b) {
            b.startReturn().string("((sp - 1) << 16) | 0xFFFF").end();
        }

        private void emitBeforeReturnProfiling(CodeTreeBuilder b) {
            if (tier.isUncached()) {
                b.statement("uncachedExecuteCount--");
                b.startIf().string("uncachedExecuteCount <= 0").end().startBlock();
                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                b.statement("this.getRoot().transitionToCached()");
                b.end().startElseBlock();
                b.statement("this.uncachedExecuteCount_ = uncachedExecuteCount");
                b.end();
            } else {
                emitReportLoopCount(b, CodeTreeBuilder.singleString("loopCounter.value > 0"), false);
            }
        }

        /**
         * To avoid storing the bci in cases when the operation is simple, we use the heuristic that
         * a node will not escape/read its own bci unless it has a cached value.
         *
         * Note: the caches list includes bind values, so @Bind("$root") is included in the check.
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

    class OSRMembersFactory {
        static final String METADATA_FIELD_NAME = "osrMetadata_";

        private List<CodeElement<Element>> create() {
            List<CodeElement<Element>> result = new ArrayList<>();

            result.add(createExecuteOSR());
            result.addAll(createMetadataMembers());
            result.addAll(createStoreAndRestoreParentFrameMethods());

            return result;
        }

        private CodeExecutableElement createExecuteOSR() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeOSRNode, "executeOSR");
            ex.renameArguments("frame", "target", model.enableYield ? "localFrame" : "unused");
            CodeTreeBuilder b = ex.getBuilder();
            b.startReturn().startCall("continueAt");
            b.string("getRoot()");
            b.string("frame");
            if (model.enableYield) {
                b.string("localFrame == null ? frame : (MaterializedFrame) localFrame");
            }
            b.string("target");
            b.end(2);

            return ex;
        }

        private List<CodeElement<Element>> createMetadataMembers() {
            CodeVariableElement osrMetadataField = compFinal(new CodeVariableElement(Set.of(PRIVATE), context.getDeclaredType(Object.class), METADATA_FIELD_NAME));

            CodeExecutableElement getOSRMetadata = GeneratorUtils.overrideImplement(types.BytecodeOSRNode, "getOSRMetadata");
            getOSRMetadata.getBuilder().startReturn().string(METADATA_FIELD_NAME).end();

            CodeExecutableElement setOSRMetadata = GeneratorUtils.overrideImplement(types.BytecodeOSRNode, "setOSRMetadata");
            setOSRMetadata.getBuilder().startAssign(METADATA_FIELD_NAME).variable(setOSRMetadata.getParameters().get(0)).end();

            return List.of(osrMetadataField, getOSRMetadata, setOSRMetadata);
        }

        private List<CodeExecutableElement> createStoreAndRestoreParentFrameMethods() {
            // Append parent frame to end of array so that regular argument reads work as expected.
            CodeExecutableElement storeParentFrameInArguments = GeneratorUtils.overrideImplement(types.BytecodeOSRNode, "storeParentFrameInArguments");
            CodeTreeBuilder sb = storeParentFrameInArguments.getBuilder();
            sb.declaration(context.getType(Object[].class), "parentArgs", "parentFrame.getArguments()");
            sb.declaration(context.getType(Object[].class), "result", "Arrays.copyOf(parentArgs, parentArgs.length + 1)");
            sb.statement("result[result.length - 1] = parentFrame");
            sb.startReturn().string("result").end();

            CodeExecutableElement restoreParentFrameFromArguments = GeneratorUtils.overrideImplement(types.BytecodeOSRNode, "restoreParentFrameFromArguments");
            CodeTreeBuilder rb = restoreParentFrameFromArguments.getBuilder();
            rb.startReturn().cast(types.Frame).string("arguments[arguments.length - 1]").end();

            return List.of(storeParentFrameInArguments, restoreParentFrameFromArguments);
        }

    }

    class BytecodeLabelImplFactory {
        private CodeTypeElement create() {
            bytecodeLabelImpl.setSuperClass(generic(types.BytecodeLabel, model.templateType.asType()));
            bytecodeLabelImpl.setEnclosingElement(bytecodeNodeGen);

            bytecodeLabelImpl.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), context.getType(int.class), "id"));
            bytecodeLabelImpl.add(new CodeVariableElement(context.getType(int.class), "bci"));
            bytecodeLabelImpl.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), context.getType(int.class), "declaringOp"));

            bytecodeLabelImpl.add(createConstructorUsingFields(Set.of(), bytecodeLabelImpl, null));
            bytecodeLabelImpl.add(createIsDefined());
            bytecodeLabelImpl.add(createEquals());
            bytecodeLabelImpl.add(createHashCode());

            return bytecodeLabelImpl;
        }

        private CodeExecutableElement createIsDefined() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), context.getType(boolean.class), "isDefined");
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn().string("bci != -1").end();
            return ex;
        }

        private CodeExecutableElement createEquals() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), context.getType(boolean.class), "equals");
            ex.addParameter(new CodeVariableElement(context.getType(Object.class), "other"));

            CodeTreeBuilder b = ex.createBuilder();
            b.startIf().string("!(other instanceof BytecodeLabelImpl)").end().startBlock();
            b.returnFalse();
            b.end();

            b.startReturn().string("this.id == ((BytecodeLabelImpl) other).id").end();
            return ex;
        }

        private CodeExecutableElement createHashCode() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), context.getType(int.class), "hashCode");
            CodeTreeBuilder b = ex.createBuilder();

            b.startReturn().string("this.id").end();
            return ex;
        }
    }

    class ContinuationRootNodeImplFactory {
        private CodeTypeElement create() {
            continuationRootNodeImpl.setEnclosingElement(bytecodeNodeGen);
            continuationRootNodeImpl.setSuperClass(types.ContinuationRootNode);

            continuationRootNodeImpl.add(new CodeVariableElement(Set.of(FINAL), bytecodeNodeGen.asType(), "root"));
            continuationRootNodeImpl.add(new CodeVariableElement(Set.of(FINAL), context.getType(int.class), "sp"));
            continuationRootNodeImpl.add(compFinal(new CodeVariableElement(Set.of(VOLATILE), types.BytecodeLocation, "location")));
            continuationRootNodeImpl.add(GeneratorUtils.createConstructorUsingFields(
                            Set.of(), continuationRootNodeImpl,
                            ElementFilter.constructorsIn(((TypeElement) types.RootNode.asElement()).getEnclosedElements()).stream().filter(x -> x.getParameters().size() == 2).findFirst().get()));

            continuationRootNodeImpl.add(createExecute());
            continuationRootNodeImpl.add(createGetSourceRootNode());
            continuationRootNodeImpl.add(createGetLocation());
            continuationRootNodeImpl.add(createFindFrame());
            continuationRootNodeImpl.add(createUpdateBytecodeLocation());
            continuationRootNodeImpl.add(createUpdateBytecodeLocationWithoutInvalidate());
            continuationRootNodeImpl.add(createCreateContinuation());
            continuationRootNodeImpl.add(createToString());

            // RootNode overrides.
            continuationRootNodeImpl.add(createIsCloningAllowed());
            continuationRootNodeImpl.add(createIsCloneUninitializedSupported());
            // Should appear last. Uses current method set to determine which methods need to be
            // implemented.
            continuationRootNodeImpl.addAll(createRootNodeProxyMethods());

            return continuationRootNodeImpl;
        }

        private CodeExecutableElement createExecute() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.RootNode, "execute");
            ex.renameArguments("frame");

            CodeTreeBuilder b = ex.createBuilder();

            b.statement("Object[] args = frame.getArguments()");
            b.startIf().string("args.length != 2").end().startBlock();
            b.tree(GeneratorUtils.createShouldNotReachHere("Expected 2 arguments: (parentFrame, inputValue)"));
            b.end();

            b.declaration(types.MaterializedFrame, "parentFrame", "(MaterializedFrame) args[0]");
            b.declaration(context.getType(Object.class), "inputValue", "args[1]");

            b.startIf().string("parentFrame.getFrameDescriptor() != frame.getFrameDescriptor()").end().startBlock();
            b.tree(GeneratorUtils.createShouldNotReachHere("Invalid continuation parent frame passed"));
            b.end();

            b.lineComment("Copy any existing stack values (from numLocals to sp - 1) to the current frame, which will be used for stack accesses.");
            b.statement(copyFrameTo("parentFrame", "root.numLocals", "frame", "root.numLocals", "sp - 1"));
            b.statement(setFrameObject(COROUTINE_FRAME_IDX, "parentFrame"));
            b.statement(setFrameObject("root.numLocals + sp - 1", "inputValue"));
            b.declaration(types.BytecodeLocation, "bytecodeLocation", "location");
            b.statement("int startState = ((sp + root.numLocals) << 16) | (bytecodeLocation.getBytecodeIndex() & 0xFFFF)");

            b.startReturn();
            b.startCall("root.continueAt");
            b.startGroup().cast(abstractBytecodeNode.asType()).string("bytecodeLocation.getBytecodeNode()").end();
            b.string("startState");
            b.string("frame");
            b.string("parentFrame");
            b.string("this");
            b.end(2);

            return ex;
        }

        private CodeExecutableElement createGetSourceRootNode() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.ContinuationRootNode, "getSourceRootNode");
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn().string("root").end();
            return ex;
        }

        private CodeExecutableElement createGetLocation() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.ContinuationRootNode, "getLocation");
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn().string("location").end();
            return ex;
        }

        private CodeExecutableElement createFindFrame() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.ContinuationRootNode, "findFrame");
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn();
            b.cast(types.Frame);
            startGetFrame(b, "frame", type(Object.class), false).string(COROUTINE_FRAME_IDX).end();
            b.end();
            return ex;
        }

        private CodeExecutableElement createUpdateBytecodeLocation() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "updateBytecodeLocation");
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
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "updateBytecodeLocationWithoutInvalidate");
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
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(context.getDeclaredType(Object.class), "toString");
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn();
            b.startStaticCall(type(String.class), "format");
            b.doubleQuote(ElementUtils.getSimpleName(types.ContinuationRootNode) + " [location=%s]");
            b.string("location");
            b.end(2);
            return ex;
        }

        private CodeExecutableElement createIsCloningAllowed() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.RootNode, "isCloningAllowed");
            CodeTreeBuilder b = ex.createBuilder();
            b.lineComment("Continuations are one-to-one with root nodes.");
            b.startReturn();
            b.string("false");
            b.end();
            return ex;
        }

        private CodeExecutableElement createIsCloneUninitializedSupported() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.RootNode, "isCloneUninitializedSupported");
            CodeTreeBuilder b = ex.createBuilder();
            b.lineComment("Continuations are one-to-one with root nodes.");
            b.startReturn();
            b.string("false");
            b.end();
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

                CodeExecutableElement proxyMethod = GeneratorUtils.overrideImplement(templateMethod);
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

    class ContinuationLocationFactory {
        private CodeTypeElement create() {
            continuationLocation.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), context.getType(int.class), "constantPoolIndex"));
            continuationLocation.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), context.getType(int.class), "bci"));
            continuationLocation.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), context.getType(int.class), "sp"));
            continuationLocation.add(GeneratorUtils.createConstructorUsingFields(Set.of(), continuationLocation));

            return continuationLocation;
        }
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

        if (BytecodeDSLNodeFactory.this.model.enableUncachedInterpreter) {
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
        b.startStatement().startStaticCall(context.getType(VarHandle.class), "storeStoreFence").end(2);
    }

    private void emitThrowIllegalStateException(CodeTreeBuilder b, String reason) {
        emitThrow(b, IllegalStateException.class, reason);
    }

    private static void emitThrowAssertionError(CodeTreeBuilder b, String reason) {
        b.startThrow().startCall("assertionFailed").string(reason).end(2);
    }

    private void emitThrow(CodeTreeBuilder b, Class<? extends Throwable> exceptionClass, String reasonCode) {
        b.startThrow().startNew(context.getType(exceptionClass));
        if (reasonCode != null) {
            b.string(reasonCode);
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

    private static CodeTree cast(TypeMirror type, String value) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.cast(type);
        b.string(value);
        return b.build();
    }

    private String localFrame() {
        return model.enableYield ? "localFrame" : "frame";
    }

    // Helpers to generate common strings
    private static String readBc(String index) {
        return readBc("bc", index);
    }

    public static String readBc(String bc, String index) {
        return String.format("ACCESS.shortArrayRead(%s, %s)", bc, index);
    }

    private static String readConst(String index) {
        return readConst(index, "constants");
    }

    public static String readConst(String index, String constants) {
        return String.format("ACCESS.objectArrayRead(%s, %s)", constants, index);
    }

    private static CodeTree readTagNode(TypeMirror expectedType, CodeTree index) {
        return readTagNode(expectedType, "tagRoot.tagNodes", index);
    }

    private static CodeTree readTagNode(TypeMirror expectedType, String tagNodes, CodeTree index) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.startCall("ACCESS.cast");
        b.startCall("ACCESS.objectArrayRead");
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
        b.startCall("ACCESS.cast");
        b.startCall("ACCESS.objectArrayRead");
        b.string("cachedNodes");
        b.tree(index);
        b.end();
        b.typeLiteral(expectedType);
        b.end();
        return b.build();
    }

    private static String getFrameObject(String index) {
        return getFrameObject("frame", index);
    }

    private static String getFrameObject(String frame, String index) {
        return String.format("ACCESS.uncheckedGetObject(%s, %s)", frame, index);
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
        b.startCall("ACCESS", methodName);
        return b;
    }

    static CodeTreeBuilder startExpectFrameUnsafe(CodeTreeBuilder b, String frame, TypeMirror type) {
        return startExpectFrame(b, frame, type, true);
    }

    static CodeTreeBuilder startIsFrame(CodeTreeBuilder b, String frame, TypeMirror type) {
        String methodName;
        switch (type.getKind()) {
            case BOOLEAN:
                methodName = "isBoolean";
                break;
            case BYTE:
                methodName = "isByte";
                break;
            case INT:
                methodName = "isInt";
                break;
            case LONG:
                methodName = "isLong";
                break;
            case FLOAT:
                methodName = "isFloat";
                break;
            case DOUBLE:
                methodName = "isDouble";
                break;
            default:
                methodName = "isObject";
                break;
        }
        b.startCall(frame, methodName);
        return b;
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
            b.startCall("ACCESS", methodName);
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
            b.startCall("ACCESS", methodName);
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
        b.startCall("ACCESS", methodName);
        return b;
    }

    private static String setFrameObject(String index, String value) {
        return setFrameObject("frame", index, value);
    }

    private static String setFrameObject(String frame, String index, String value) {
        return String.format("ACCESS.setObject(%s, %s, %s)", frame, index, value);
    }

    private static String clearFrame(String frame, String index) {
        return String.format("ACCESS.clear(%s, %s)", frame, index);
    }

    private static String copyFrameSlot(String src, String dst) {
        return String.format("ACCESS.copy(frame, %s, %s)", src, dst);
    }

    private static String copyFrameTo(String srcFrame, String srcOffset, String dstFrame, String dstOffset, String length) {
        return String.format("ACCESS.copyTo(%s, %s, %s, %s, %s)", srcFrame, srcOffset, dstFrame, dstOffset, length);
    }

    private static String cachedDataClassName(InstructionModel instr) {
        if (!instr.isCustomInstruction()) {
            return null;
        }
        if (instr.quickeningBase != null) {
            return cachedDataClassName(instr.quickeningBase);
        }
        return instr.getInternalName() + "Gen";
    }

    private static String childString(int numChildren) {
        return numChildren + ((numChildren == 1) ? " child" : " children");
    }

    private static CodeTree readInstruction(String bc, String bci) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.startCall("ACCESS", "shortArrayRead");
        b.string(bc);
        b.startGroup();
        b.string(bci);
        b.end();
        b.end();
        return b.build();
    }

    private static CodeTree readImmediate(String bc, String bci, InstructionImmediate immediate) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.startCall("ACCESS", "shortArrayRead");
        b.string(bc);
        b.startGroup();
        b.string(bci).string(" + ").string(immediate.offset()).string(" ");
        b.startComment().string(" imm ", immediate.name(), " ").end();
        b.end();
        b.end();
        return b.build();
    }

    /**
     * User code directly references some generated types and methods, like builder methods. When
     * there is an error in the model, this factory generates stubs for the user-accessible names to
     * prevent the compiler from emitting many unhelpful error messages about unknown types/methods.
     */
    public static final class ErrorFactory {
        private final ProcessorContext context = ProcessorContext.getInstance();
        private final TruffleTypes types = context.getTypes();

        private final BytecodeDSLModel model;
        private final CodeTypeElement bytecodeNodeGen;

        private final CodeTypeElement builder = new CodeTypeElement(Set.of(PUBLIC, STATIC, FINAL), ElementKind.CLASS, null, "Builder");
        private final DeclaredType operationBuilderType = new GeneratedTypeMirror("", builder.getSimpleName().toString(), builder.asType());
        private final TypeMirror parserType = generic(types.BytecodeParser, operationBuilderType);

        public ErrorFactory(BytecodeDSLModel model) {
            assert model.hasErrors();
            this.model = model;
            this.bytecodeNodeGen = GeneratorUtils.createClass(model.templateType, null, Set.of(PUBLIC, FINAL), model.getName(), model.templateType.asType());
        }

        public CodeTypeElement create() {
            bytecodeNodeGen.add(createExecute());
            bytecodeNodeGen.add(createConstructor());
            bytecodeNodeGen.add(createCreate());
            if (model.enableSerialization) {
                bytecodeNodeGen.add(createSerialize());
                bytecodeNodeGen.add(createDeserialize());
            }

            bytecodeNodeGen.add(new BuilderFactory().create());
            bytecodeNodeGen.add(createNewConfigBuilder());
            return bytecodeNodeGen;
        }

        private CodeExecutableElement createExecute() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.RootNode, "execute");
            CodeTreeBuilder b = ex.createBuilder();
            emitThrowNotImplemented(b);
            return ex;
        }

        private CodeExecutableElement createConstructor() {
            CodeExecutableElement ctor = new CodeExecutableElement(Set.of(PRIVATE), null, bytecodeNodeGen.getSimpleName().toString());
            ctor.addParameter(new CodeVariableElement(types.TruffleLanguage, "language"));
            ctor.addParameter(new CodeVariableElement(types.FrameDescriptor_Builder, "builder"));
            CodeTreeBuilder b = ctor.getBuilder();
            b.startStatement().startCall("super");
            b.string("language");
            if (model.fdBuilderConstructor != null) {
                b.string("builder");
            } else {
                b.string("builder.build()");
            }
            b.end(2);
            emitThrowNotImplemented(b);
            return ctor;
        }

        private CodeExecutableElement createCreate() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC, STATIC), generic(types.BytecodeRootNodes, model.templateType.asType()), "create");
            ex.addParameter(new CodeVariableElement(types.BytecodeConfig, "config"));
            ex.addParameter(new CodeVariableElement(generic(types.BytecodeParser, builder.asType()), "generator"));
            CodeTreeBuilder b = ex.getBuilder();
            emitThrowNotImplemented(b);
            return ex;
        }

        private CodeExecutableElement createSerialize() {
            CodeExecutableElement method = new CodeExecutableElement(Set.of(PUBLIC, STATIC), context.getType(void.class), "serialize");
            method.addParameter(new CodeVariableElement(types.BytecodeConfig, "config"));
            method.addParameter(new CodeVariableElement(context.getType(DataOutput.class), "buffer"));
            method.addParameter(new CodeVariableElement(types.BytecodeSerializer, "callback"));
            method.addParameter(new CodeVariableElement(parserType, "parser"));
            method.addThrownType(context.getType(IOException.class));
            CodeTreeBuilder b = method.createBuilder();
            emitThrowNotImplemented(b);
            return method;
        }

        private CodeExecutableElement createDeserialize() {
            CodeExecutableElement method = new CodeExecutableElement(Set.of(PUBLIC, STATIC),
                            generic(types.BytecodeRootNodes, model.getTemplateType().asType()), "deserialize");
            method.addParameter(new CodeVariableElement(types.TruffleLanguage, "language"));
            method.addParameter(new CodeVariableElement(types.BytecodeConfig, "config"));
            method.addParameter(new CodeVariableElement(generic(Supplier.class, DataInput.class), "input"));
            method.addParameter(new CodeVariableElement(types.BytecodeDeserializer, "callback"));
            method.addThrownType(context.getType(IOException.class));
            CodeTreeBuilder b = method.createBuilder();
            emitThrowNotImplemented(b);
            return method;
        }

        private CodeExecutableElement createNewConfigBuilder() {
            CodeExecutableElement method = new CodeExecutableElement(Set.of(PUBLIC, STATIC), types.BytecodeConfig_Builder, "newConfigBuilder");
            CodeTreeBuilder b = method.createBuilder();
            emitThrowNotImplemented(b);
            return method;
        }

        private void emitThrowNotImplemented(CodeTreeBuilder b) {
            b.startThrow().startNew(context.getType(AbstractMethodError.class));
            b.string("\"There are error(s) with the operation node specification. Please resolve the error(s) and recompile.\"");
            b.end(2);
        }

        private final class BuilderFactory {
            private CodeTypeElement create() {
                builder.setEnclosingElement(bytecodeNodeGen);
                mergeSuppressWarnings(builder, "all");

                builder.add(createMethodStub("createLocal", types.BytecodeLocal));
                builder.add(createMethodStub("createLabel", types.BytecodeLabel));

                for (OperationModel operation : model.getOperations()) {
                    /**
                     * If parsing fails, we may not know if the operation takes dynamic operands
                     * (e.g., it could have only constant operands). Conservatively generate stubs
                     * for all three builder methods.
                     */
                    builder.add(createBegin(operation));
                    builder.add(createEnd(operation));
                    builder.add(createEmit(operation));
                }

                return builder;
            }

            private CodeExecutableElement createMethodStub(String name, TypeMirror returnType) {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), returnType, name);
                ex.addParameter(new CodeVariableElement(context.getDeclaredType(Object.class), "args"));
                ex.setVarArgs(true);
                emitThrowNotImplemented(ex.createBuilder());
                return ex;
            }

            private CodeExecutableElement createBegin(OperationModel operation) {
                return createMethodStub("begin" + operation.name, context.getType(void.class));
            }

            private CodeExecutableElement createEnd(OperationModel operation) {
                return createMethodStub("end" + operation.name,
                                operation.kind == OperationKind.ROOT ? model.templateType.asType() : context.getType(void.class));
            }

            private CodeExecutableElement createEmit(OperationModel operation) {
                return createMethodStub("emit" + operation.name, context.getType(void.class));
            }
        }
    }
}
