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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOError;
import java.io.IOException;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.ImmediateKind;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionImmediate;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionKind;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel.OperationArgument;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel.OperationKind;
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

    private final ProcessorContext context = ProcessorContext.getInstance();
    private final TruffleTypes types = context.getTypes();
    private final BytecodeDSLModel model;

    // All of the following CodeTypeElements represent classes generated by this factory. Some are
    // conditionally generated depending on the model, so they get initialized in the constructor.
    // After initialization, the code for each class must still be generated; this is done by the
    // XYZFactory classes.

    // The top-level class that subclasses the node annotated with @GenerateBytecode.
    // All of the definitions that follow are nested inside of this class.
    private final CodeTypeElement bytecodeNodeGen;

    // The builder class invoked by the language parser to generate the bytecode.
    private final CodeTypeElement builder = new CodeTypeElement(Set.of(PUBLIC, STATIC, FINAL), ElementKind.CLASS, null, "Builder");
    private final DeclaredType bytecodeBuilderType = new GeneratedTypeMirror("", builder.getSimpleName().toString(), builder.asType());
    private final TypeMirror parserType = generic(types.BytecodeParser, bytecodeBuilderType);

    // Implementations of public classes that Truffle interpreters interact with.
    private final CodeTypeElement bytecodeRootNodesImpl = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "BytecodeRootNodesImpl");
    private final CodeTypeElement bytecodeLocalImpl = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "BytecodeLocalImpl");
    private final CodeTypeElement bytecodeLabelImpl = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "BytecodeLabelImpl");

    // Helper classes that map instructions/operations to constant integral values.
    private final CodeTypeElement instructionsElement = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "Instructions");
    private final CodeTypeElement operationsElement = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "Operations");

    // Interface representing data objects that can have a specified boxing state.
    private final CodeTypeElement boxableInterface = new CodeTypeElement(Set.of(PRIVATE), ElementKind.INTERFACE, null, "BoxableInterface");

    // Helper class that tracks the number of guest-language loop iterations. The count must be
    // wrapped in an object, otherwise the loop unrolling logic of ExplodeLoop.MERGE_EXPLODE will
    // create a new "state" for each count.
    private final CodeTypeElement loopCounter = new CodeTypeElement(Set.of(PRIVATE, STATIC), ElementKind.CLASS, null, "LoopCounter");

    // Root node and ContinuationLocal classes to support yield.
    private final CodeTypeElement continuationRootNodeImpl;
    private final CodeTypeElement continuationLocationImpl;

    // Singleton field for an empty array.
    private final CodeVariableElement emptyObjectArray;

    // Singleton field for accessing arrays and the frame.
    private final CodeVariableElement fastAccess;

    private CodeTypeElement abstractBytecodeNode;

    // Represents the index that user locals start from. Depends on the number of reserved slots.
    private int userLocalsStartIndex;

    private final Map<TypeMirror, VariableElement> frameSlotKindConstant = new HashMap<>();

    public BytecodeDSLNodeFactory(BytecodeDSLModel model) {
        this.model = model;
        bytecodeNodeGen = GeneratorUtils.createClass(model.templateType, null, Set.of(PUBLIC, FINAL), model.getName(), model.templateType.asType());
        emptyObjectArray = addField(bytecodeNodeGen, Set.of(PRIVATE, STATIC, FINAL), Object[].class, "EMPTY_ARRAY", "new Object[0]");
        fastAccess = addField(bytecodeNodeGen, Set.of(PRIVATE, STATIC, FINAL), types.BytecodeDSLAccess, "ACCESS");
        fastAccess.setInit(createFastAccessFieldInitializer());

        if (model.enableYield) {
            continuationRootNodeImpl = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "ContinuationRootNodeImpl");
            continuationLocationImpl = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "ContinuationLocationImpl");
        } else {
            continuationRootNodeImpl = null;
            continuationLocationImpl = null;
        }
    }

    public CodeTypeElement create() {
        // Print a summary of the model in a docstring at the start.
        addDoc(bytecodeNodeGen, false, model.pp());

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

        this.abstractBytecodeNode = bytecodeNodeGen.add(new AbstractBytecodeNodeFactory().create());

        CodeVariableElement bytecodeNode = new CodeVariableElement(Set.of(PRIVATE, VOLATILE), abstractBytecodeNode.asType(), "bytecode");
        bytecodeNodeGen.add(compFinal(bytecodeNode));
        bytecodeNodeGen.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), bytecodeRootNodesImpl.asType(), "nodes"));
        bytecodeNodeGen.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), context.getType(int.class), "numLocals"));
        bytecodeNodeGen.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), context.getType(int.class), "numNodes"));
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

        // Define implementations for the public classes that Truffle interpreters interact with.
        bytecodeNodeGen.add(new BytecodeRootNodesImplFactory().create());
        bytecodeNodeGen.add(new BytecodeLocalImplFactory().create());
        bytecodeNodeGen.add(new BytecodeLabelImplFactory().create());

        // Define helper classes containing the constants for instructions and operations.
        bytecodeNodeGen.add(new InstructionConstantsFactory().create());
        bytecodeNodeGen.add(new OperationsConstantsFactory().create());

        // Define the classes that model instruction data (e.g., cache data, continuation data).
        bytecodeNodeGen.add(new BoxableInterfaceFactory().create());

        // Define the classes that implement continuations (yield).
        if (model.enableYield) {
            bytecodeNodeGen.add(new ContinuationRootNodeImplFactory().create());
            bytecodeNodeGen.add(new ContinuationLocationImplFactory().create());
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
        bytecodeNodeGen.add(createAddSource());
        bytecodeNodeGen.add(createEnsureSources());

        bytecodeNodeGen.add(createEncodeInstrumentation());
        bytecodeNodeGen.add(createEncodeTags());

        // Define a loop counter class to track how many back-edges have been taken.
        bytecodeNodeGen.add(createLoopCounter());

        // Define the static method to create a root node.
        bytecodeNodeGen.add(createCreate());

        // Define serialization methods and helper fields.
        if (model.enableSerialization) {
            bytecodeNodeGen.add(createSerialize());
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
        bytecodeNodeGen.add(createGetLocalIndex());
        bytecodeNodeGen.add(createGetLocal());
        bytecodeNodeGen.add(createGetLocals());
        bytecodeNodeGen.add(createGetLocalNames());
        bytecodeNodeGen.add(createGetLocalInfos());
        bytecodeNodeGen.add(createGetBytecodeNode());
        bytecodeNodeGen.add(createGetSourceSection());
        bytecodeNodeGen.addAll(createCopyLocals());

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
            el.setSuperClass(types.Node);
            factory.create(el);

            el.add(createNodeGetSourceSection(cachedBytecodeNode));

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

        return bytecodeNodeGen;
    }

    private CodeExecutableElement createNodeGetSourceSection(CodeTypeElement cachedBytecodeNode) {
        CodeExecutableElement ex = GeneratorUtils.override(types.Node, "getSourceSection");
        CodeTreeBuilder b = ex.createBuilder();
        b.declaration(cachedBytecodeNode.asType(), "bytecode", CodeTreeBuilder.createBuilder().cast(cachedBytecodeNode.asType(), "getParent()").build());
        b.declaration(type(int.class), "bci", "bytecode.findBytecodeIndexOfOperationNode(this)");
        b.startReturn().string("bytecode.findSourceLocation(bci)").end();
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
                addDoc(tag, false, "FrameSlotKind." + frameSlotKind + ".tag");
                tag.createInitBuilder().string(index);
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
        CodeVariableElement[] parameterTypes = new CodeVariableElement[instruction.signature.valueCount];
        for (int i = 0; i < instruction.signature.valueCount; i++) {
            parameterTypes[i] = new CodeVariableElement(instruction.signature.getGenericType(i), "var" + i);
        }

        TypeMirror returnType = instruction.signature.returnType;
        CodeExecutableElement executable = new CodeExecutableElement(Set.of(PRIVATE),
                        returnType, executeMethodName(instruction),
                        new CodeVariableElement(types.VirtualFrame, "frameValue"));

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

        int uncachedBciIndex = -1;
        if (model.needsBciSlot()) {
            uncachedBciIndex = reserved++;
            result.add(createInitializedVariable(Set.of(PRIVATE, STATIC, FINAL), int.class, BCI_IDX, uncachedBciIndex + ""));
        }

        int coroutineFrameIndex = 1;
        if (model.enableYield) {
            coroutineFrameIndex = reserved++;
            result.add(createInitializedVariable(Set.of(PRIVATE, STATIC, FINAL), int.class, COROUTINE_FRAME_IDX, coroutineFrameIndex + ""));
        }

        result.add(createInitializedVariable(Set.of(PRIVATE, STATIC, FINAL), int.class, USER_LOCALS_START_IDX, reserved + ""));
        userLocalsStartIndex = reserved;

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
        ctor.addParameter(new CodeVariableElement(type(short[].class), "bytecodes"));
        ctor.addParameter(new CodeVariableElement(type(Object[].class), "constants"));
        ctor.addParameter(new CodeVariableElement(type(int[].class), "handlers"));
        ctor.addParameter(new CodeVariableElement(type(int[].class), "sourceInfo"));
        ctor.addParameter(new CodeVariableElement(generic(type(List.class), types.Source), "sources"));
        ctor.addParameter(new CodeVariableElement(type(int.class), "numLocals"));
        ctor.addParameter(new CodeVariableElement(type(int.class), "numNodes"));
        ctor.addParameter(new CodeVariableElement(type(int.class), "buildIndex"));

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
        b.statement("this.numNodes = numNodes");
        b.statement("this.buildIndex = buildIndex");
        b.startStatement();
        b.string("this.bytecode = ");
        b.startCall("insert");
        b.startNew(initialBytecodeNode.asType());
        b.string("bytecodes");
        b.string("constants");
        b.string("handlers");
        b.string("sourceInfo");
        b.string("sources");
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
        b.string("frame");
        if (model.enableYield) {
            b.string("frame");
        }
        b.string("numLocals << 16");
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
        }
        ex.addParameter(new CodeVariableElement(context.getType(int.class), "startState"));

        CodeTreeBuilder b = ex.createBuilder();
        String localFrame = localFrame();

        if (model.executeEpilog != null) {
            b.statement("Throwable throwable = null");
            b.statement("Object returnValue = null");
        }

        if (model.executeProlog != null) {
            b.statement("this.executeProlog(" + localFrame + ")");
        }

        if (model.executeEpilog != null) {
            b.startTryBlock();
        }

        b.statement("int state = startState");
        // These don't change between invocations. Read them once.
        b.statement("AbstractBytecodeNode bc = this.bytecode");

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

        b.startIf().string("(state & 0xffff) == 0xffff").end().startBlock();
        b.statement("break");
        b.end().startElseBlock();
        b.lineComment("Bytecode or tier changed");
        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
        b.statement("bc = this.bytecode");
        b.end();
        b.end();

        String returnValue = getFrameObject("(state >> 16) & 0xffff");
        if (model.executeEpilog != null) {
            b.startAssign("returnValue").string(returnValue).end();
            b.statement("return returnValue");
        } else {
            b.startReturn().string(returnValue).end();
        }

        if (model.executeEpilog != null) {
            b.end().startCatchBlock(context.getType(Throwable.class), "th");
            b.statement("throwable = th");
            b.statement("throw th");
            b.end().startFinallyBlock();
            b.statement("this.executeEpilog(" + localFrame + ", returnValue, throwable)");
            b.end();
        }

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

        addDoc(ex, true, String.format("""
                        Creates one or more bytecode nodes. This is the entrypoint for creating new {@link %s} instances.

                        @param config indicates whether to parse metadata (e.g., source information).
                        @param parser the parser that invokes a series of builder instructions to generate bytecode.
                        """, model.getName()));

        CodeTreeBuilder b = ex.getBuilder();

        b.declaration("BytecodeRootNodesImpl", "nodes", "new BytecodeRootNodesImpl(parser)");
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

        addDoc(method, true, """
                        Serializes the bytecode nodes parsed by the {@code parser}.
                        All metadata (e.g., source info) is serialized (even if it has not yet been parsed).

                        @param buffer the buffer to write the byte output to.
                        @param callback the language-specific serializer for constants in the bytecode.
                        @param parser the parser.
                        """);

        CodeTreeBuilder init = CodeTreeBuilder.createBuilder();
        init.startNew(bytecodeBuilderType);
        init.startGroup();
        init.startNew(bytecodeRootNodesImpl.asType());
        init.string("parser");
        init.end(2);
        init.staticReference(types.BytecodeConfig, "COMPLETE");
        init.end();

        CodeTreeBuilder b = method.createBuilder();

        b.declaration(bytecodeBuilderType, "builder", init.build());

        b.startTryBlock();

        b.startStatement().startCall("builder", "serialize");
        b.string("buffer");
        b.string("callback");
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

        addDoc(method, true,
                        """
                                        Deserializes a byte sequence to bytecode nodes. The bytes must have been produced by a previous call to {@link #serialize}.").newLine()

                                        @param language the language instance.
                                        @param config indicates whether to deserialize metadata (e.g., source information), if available.
                                        @param input A function that supplies the bytes to deserialize. This supplier must produce a new {@link DataInput} each time, since the bytes may be processed multiple times for reparsing.
                                        @param callback The language-specific deserializer for constants in the bytecode. This callback must perform the inverse of the callback that was used to {@link #serialize} the nodes to bytes.
                                        """);

        CodeTreeBuilder b = method.createBuilder();

        b.startTryBlock();

        b.statement("return create(config, (b) -> b.deserialize(language, input, callback))");
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

    private CodeExecutableElement createGetLocalIndex() {
        CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeRootNode, "getLocalIndex");

        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn();
        b.startParantheses();
        b.cast(bytecodeLocalImpl.asType(), "local");
        b.end();
        b.string(".index - " + USER_LOCALS_START_IDX);
        b.end();

        return ex;
    }

    private CodeExecutableElement createGetLocal() {
        CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeRootNode, "getLocal");

        CodeTreeBuilder b = ex.createBuilder();

        String index = "localIndex + " + USER_LOCALS_START_IDX;

        if (model.usesBoxingElimination()) {
            b.startTryBlock();
            b.startSwitch().string("getFrameDescriptor().getSlotKind(" + index + ")").end().startBlock();
            for (TypeMirror type : model.boxingEliminatedTypes) {
                b.startCase().string(ElementUtils.firstLetterUpperCase(ElementUtils.getSimpleName(type))).end();
                b.startCaseBlock();
                b.startReturn();
                startExpectFrame(b, "frame", type, false).string(index).end();
                b.end(); // return
                b.end(); // case block
            }

            b.startCase().string("Object").end();
            b.startCase().string("Illegal").end();
            b.startCaseBlock();
            b.startReturn();
            startExpectFrame(b, "frame", context.getType(Object.class), false).string(index).end();
            b.end(); // return
            b.end(); // case block

            b.caseDefault().startCaseBlock();
            b.tree(GeneratorUtils.createShouldNotReachHere("unexpected slot"));
            b.end();

            b.end(); // switch block

            b.end().startCatchBlock(types.UnexpectedResultException, "ex");
            b.startReturn().string("ex.getResult()").end();

            b.end(); // catch
        } else {
            b.startReturn().string("frame.getObject(" + index + ")").end();
        }

        return ex;
    }

    private CodeExecutableElement createGetLocals() {
        CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeRootNode, "getLocals");
        ex.addAnnotationMirror(createExplodeLoopAnnotation(null));

        CodeTreeBuilder b = ex.createBuilder();

        b.declaration(context.getType(Object[].class), "result", "new Object[numLocals - " + USER_LOCALS_START_IDX + "]");

        b.startFor().string("int i = 0; i < numLocals - " + USER_LOCALS_START_IDX + "; i++").end().startBlock();
        b.statement("result[i] = getLocal(frame, i)");
        b.end(); // for

        b.startReturn().string("result").end();

        return ex;
    }

    private CodeExecutableElement createGetLocalNames() {
        CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeRootNode, "getLocalNames");
        ex.addAnnotationMirror(createExplodeLoopAnnotation(null));

        CodeTreeBuilder b = ex.createBuilder();

        b.declaration(types.FrameDescriptor, "frameDescriptor", "getFrameDescriptor()");
        b.declaration(context.getType(Object[].class), "result", "new Object[numLocals - " + USER_LOCALS_START_IDX + "]");
        b.startFor().string("int i = 0; i < numLocals - " + USER_LOCALS_START_IDX + "; i++").end().startBlock();
        b.statement("result[i] = frameDescriptor.getSlotName(i + " + USER_LOCALS_START_IDX + ")");
        b.end();

        b.startReturn().string("result").end();

        return ex;
    }

    private CodeExecutableElement createGetLocalInfos() {
        CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeRootNode, "getLocalInfos");
        ex.addAnnotationMirror(createExplodeLoopAnnotation(null));

        CodeTreeBuilder b = ex.createBuilder();

        b.declaration(types.FrameDescriptor, "frameDescriptor", "getFrameDescriptor()");
        b.declaration(context.getType(Object[].class), "result", "new Object[numLocals - " + USER_LOCALS_START_IDX + "]");
        b.startFor().string("int i = 0; i < numLocals - " + USER_LOCALS_START_IDX + "; i++").end().startBlock();
        b.statement("result[i] = frameDescriptor.getSlotInfo(i + " + USER_LOCALS_START_IDX + ")");
        b.end();

        b.startReturn().string("result").end();

        return ex;
    }

    private List<CodeExecutableElement> createCopyLocals() {
        CodeExecutableElement copyAllLocals = GeneratorUtils.overrideImplement(types.BytecodeRootNode, "copyLocals", 2);
        CodeTreeBuilder copyAllLocalsBuilder = copyAllLocals.createBuilder();
        copyAllLocalsBuilder.startStatement().startCall("copyLocals");
        copyAllLocalsBuilder.string("source");
        copyAllLocalsBuilder.string("destination");
        copyAllLocalsBuilder.string("numLocals - USER_LOCALS_START_IDX");
        copyAllLocalsBuilder.end(2);

        CodeExecutableElement copyLocals = GeneratorUtils.overrideImplement(types.BytecodeRootNode, "copyLocals", 3);
        CodeTreeBuilder copyLocalsBuilder = copyLocals.createBuilder();
        copyLocalsBuilder.startStatement().startCall("source.copyTo");
        copyLocalsBuilder.string("USER_LOCALS_START_IDX");
        copyLocalsBuilder.string("destination");
        copyLocalsBuilder.string("USER_LOCALS_START_IDX");
        copyLocalsBuilder.string("length");
        copyLocalsBuilder.end(2);

        return List.of(copyAllLocals, copyLocals);
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
        b.end().startDoWhile().startCall("BYTECODE_UPDATER", "compareAndSet").string("this").string("oldBytecode").string("newBytecode").end().end();
        return ex;
    }

    private CodeExecutableElement createAddSource() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "addSource");
        ex.addParameter(new CodeVariableElement(arrayOf(type(int.class)), "sourceInfo"));
        ex.addParameter(new CodeVariableElement(generic(type(List.class), types.Source), "sources"));

        CodeTreeBuilder b = ex.createBuilder();
        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
        b.declaration(abstractBytecodeNode.asType(), "oldBytecode");
        b.declaration(abstractBytecodeNode.asType(), "newBytecode");
        b.startDoBlock();
        b.statement("oldBytecode = this.bytecode");
        b.startIf().string("oldBytecode.sourceInfo != null").end().startBlock();
        b.returnStatement();
        b.end();
        b.statement("newBytecode = insert(oldBytecode.addSource(sourceInfo, sources))");
        emitFence(b);
        b.end().startDoWhile().startCall("BYTECODE_UPDATER", "compareAndSet").string("this").string("oldBytecode").string("newBytecode").end().end();
        return ex;
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

    private static CodeExecutableElement createEncodeInstrumentation() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(int.class), "encodeInstrumentation");
        ex.addParameter(new CodeVariableElement(arrayOf(type(Class.class)), "instrumentations"));
        ex.setVarArgs(true);
        CodeTreeBuilder b = ex.createBuilder();
        b.startIf().string("instrumentations == null").end().startBlock();
        b.statement("return 0");
        b.end();

        // TODO: implement
        b.startReturn().string("0").end();
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
            b.startDeclaration(types.Instruction, "oldInstruction");
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
            b.startDeclaration(types.Instruction, "oldInstruction");
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
        b.startNew(types.Instruction).startCall(node, "parseInstruction").string(bci).tree(operand).string("null").end().end();
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
        final CodeVariableElement codeEndSerialize = addField(serializationState, Set.of(PRIVATE, STATIC, FINAL), short.class, "CODE_$END", "-5");

        final CodeVariableElement buffer = addField(serializationState, Set.of(PRIVATE, FINAL), DataOutput.class, "buffer");
        final CodeVariableElement callback = addField(serializationState, Set.of(PRIVATE, FINAL), types.BytecodeSerializer, "callback");
        final CodeExecutableElement constructor = serializationState.add(createConstructorUsingFields(Set.of(), serializationState, null));
        final CodeVariableElement language = addField(serializationState, Set.of(PRIVATE), types.TruffleLanguage, "language");

        final CodeVariableElement labelCount = addField(serializationState, Set.of(PRIVATE), int.class, "labelCount");
        final CodeVariableElement objects = addField(serializationState, Set.of(PRIVATE, FINAL),
                        generic(HashMap.class, Object.class, Short.class), "objects");

        final CodeVariableElement[] codeBegin;
        final CodeVariableElement[] codeEnd;

        SerializationStateElements() {
            serializationState.getImplements().add(types.BytecodeSerializer_SerializerContext);

            objects.createInitBuilder().startNew("HashMap<>").end();

            codeBegin = new CodeVariableElement[model.getOperations().size() + 1];
            codeEnd = new CodeVariableElement[model.getOperations().size() + 1];

            for (OperationModel o : model.getOperations()) {
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

            serializationState.add(createSerializeObject());
            serializationState.add(createWriteBytecodeNode());

        }

        private CodeExecutableElement createWriteBytecodeNode() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeSerializer_SerializerContext, "writeBytecodeNode");
            mergeSuppressWarnings(ex, "hiding");
            ex.renameArguments("buffer", "node");
            CodeTreeBuilder b = ex.createBuilder();
            b.startStatement();
            b.string("buffer.writeChar((");
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

    }

    class BuilderFactory {
        public static final String UNINIT = "UNINITIALIZED";
        CodeVariableElement uninitialized = new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), context.getType(short.class), UNINIT);

        private CodeTypeElement deserializerContextImpl = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "DeserializerContextImpl");

        CodeTypeElement savedState = new CodeTypeElement(Set.of(PRIVATE, STATIC), ElementKind.CLASS, null, "SavedState");
        CodeTypeElement operationStackEntry = new CodeTypeElement(Set.of(PRIVATE, STATIC), ElementKind.CLASS, null, "OperationStackEntry");
        CodeTypeElement finallyTryContext = new CodeTypeElement(Set.of(PRIVATE, STATIC), ElementKind.CLASS, null, "FinallyTryContext");
        CodeTypeElement constantPool = new CodeTypeElement(Set.of(PRIVATE, STATIC), ElementKind.CLASS, null, "ConstantPool");
        CodeTypeElement bytecodeLocation = new CodeTypeElement(Set.of(PRIVATE, STATIC), ElementKind.CLASS, null, "BytecodeLocation");

        TypeMirror unresolvedLabelsType = generic(HashMap.class, types.BytecodeLabel, generic(context.getDeclaredType(ArrayList.class), bytecodeLocation.asType()));

        Map<Integer, CodeExecutableElement> doEmitInstructionMethods = new TreeMap<>();

        // When we enter a FinallyTry, these fields get stored on the FinallyTryContext.
        // On exit, they are restored.
        List<CodeVariableElement> builderContextSensitiveState = new ArrayList<>(List.of(
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(short[].class), "bc"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "bci"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "currentStackHeight"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "maxStackHeight"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int[].class), "sourceInfo"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "sourceInfoIndex"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int[].class), "exHandlers"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "exHandlerCount"),
                        new CodeVariableElement(Set.of(PRIVATE), unresolvedLabelsType, "unresolvedLabels")));

        // This state is shared across all contexts for a given root node. It does not get
        // saved/restored when entering/leaving a FinallyTry.
        List<CodeVariableElement> builderContextInsensitiveState = new ArrayList<>(List.of(
                        new CodeVariableElement(Set.of(PRIVATE), types.FrameDescriptor_Builder, "frameDescriptorBuilder"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "operationSequenceNumber"),
                        new CodeVariableElement(Set.of(PRIVATE), new ArrayCodeTypeMirror(operationStackEntry.asType()), "operationStack"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "operationSp"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "numLocals"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "numLabels"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "numNodes"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "numConditionalBranches"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int[].class), "sourceIndexStack"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "sourceIndexSp"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int[].class), "sourceLocationStack"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "sourceLocationSp"),
                        new CodeVariableElement(Set.of(PRIVATE), finallyTryContext.asType(), "finallyTryContext"),
                        new CodeVariableElement(Set.of(PRIVATE), constantPool.asType(), "constantPool"),
                        // must be last
                        new CodeVariableElement(Set.of(PRIVATE), savedState.asType(), "savedState")));

        {
            if (model.enableTracing) {
                builderContextSensitiveState.add(new CodeVariableElement(Set.of(PRIVATE), context.getType(boolean[].class), "basicBlockBoundary"));
            }

            if (model.enableYield) {
                builderContextSensitiveState.add(new CodeVariableElement(Set.of(PRIVATE), generic(ArrayList.class, types.ContinuationLocation), "continuationLocations"));
                builderContextInsensitiveState.add(builderContextInsensitiveState.size() - 1, new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "numYields"));
            }
        }

        List<CodeVariableElement> builderState = Stream.of(builderContextSensitiveState, builderContextInsensitiveState).flatMap(Collection::stream).collect(Collectors.toList());

        class SavedStateFactory {
            private CodeTypeElement create() {
                savedState.addAll(builderState);
                savedState.add(createConstructorUsingFields(Set.of(), savedState, null));

                return savedState;
            }
        }

        class OperationDataClassesFactory {
            private List<CodeTypeElement> create() {
                List<CodeTypeElement> result = new ArrayList<>();

                result.add(createDataClass("RootData",
                                field(types.TruffleLanguage, "language").asFinal(),
                                field(context.getType(boolean.class), "mayFallThrough").withInitializer("true")));

                result.add(createDataClass("TransparentOperationData",
                                field(context.getType(boolean.class), "producedValue"),
                                field(context.getType(int.class), "childBci")));

                if (model.usesBoxingElimination()) {
                    result.add(createDataClass("StoreLocalData",
                                    field(new GeneratedTypeMirror("", "BytecodeLocalImpl"), "local"),
                                    field(context.getType(int.class), "childBci")));
                }

                result.add(createDataClass("IfThenData",
                                field(context.getType(int.class), "falseBranchFixupBci")));

                result.add(createDataClass("IfThenElseData",
                                field(context.getType(int.class), "falseBranchFixupBci"),
                                field(context.getType(int.class), "endBranchFixupBci")));

                if (model.usesBoxingElimination()) {
                    result.add(createDataClass("ConditionalData",
                                    field(context.getType(int.class), "falseBranchFixupBci"),
                                    field(context.getType(int.class), "endBranchFixupBci"),
                                    field(context.getType(int.class), "child0Bci"),
                                    field(context.getType(int.class), "child1Bci")));
                } else {
                    result.add(createDataClass("ConditionalData",
                                    field(context.getType(int.class), "falseBranchFixupBci"),
                                    field(context.getType(int.class), "endBranchFixupBci")));
                }

                result.add(createDataClass("WhileData",
                                field(context.getType(int.class), "whileStartBci").asFinal(),
                                field(context.getType(int.class), "endBranchFixupBci")));

                result.add(createDataClass("TryCatchData",
                                field(context.getType(int.class), "tryStartBci").asFinal(),
                                field(context.getType(int.class), "startStackHeight").asFinal(),
                                field(context.getType(int.class), "exceptionLocalIndex").asFinal(),
                                field(context.getType(int.class), "tryEndBci"),
                                field(context.getType(int.class), "catchStartBci"),
                                field(context.getType(int.class), "endBranchFixupBci")));

                result.add(createDataClass("FinallyTryData",
                                field(types.BytecodeLocal, "exceptionLocal").asFinal(),
                                field(context.getDeclaredType(Object.class), "finallyTryContext").asFinal()));

                result.add(
                                createDataClass("CustomOperationData",
                                                field(arrayOf(context.getType(int.class)), "childBcis").asFinal(),
                                                field(arrayOf(context.getDeclaredType(Object.class)), "locals").asFinal().asVarArgs()));

                if (model.isBoxingEliminated(type(boolean.class))) {
                    result.add(createDataClass("CustomShortCircuitOperationData",
                                    field(context.getType(int.class), "childBci"),
                                    field(generic(List.class, Integer.class), "branchFixupBcis").withInitializer("new ArrayList<>(4)")));
                } else {
                    result.add(createDataClass("CustomShortCircuitOperationData",
                                    field(generic(List.class, Integer.class), "branchFixupBcis").withInitializer("new ArrayList<>(4)")));
                }

                return result;
            }

            private static final class DataClassField {
                final TypeMirror type;
                final String name;
                boolean isFinal;
                boolean isVarArgs;
                // if null, field is taken as constructor parameter
                String initializer;

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

                CodeVariableElement toCodeVariableElement() {
                    Set<Modifier> mods = isFinal ? Set.of(FINAL) : Set.of();
                    return new CodeVariableElement(mods, type, name);
                }
            }

            private DataClassField field(TypeMirror type, String name) {
                return new DataClassField(type, name);
            }

            private CodeTypeElement createDataClass(String name, DataClassField... fields) {
                CodeTypeElement result = new CodeTypeElement(Set.of(Modifier.STATIC, Modifier.FINAL), ElementKind.CLASS, null, name);

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

        class OperationStackEntryFactory {
            private CodeTypeElement create() {
                List<CodeVariableElement> fields = List.of(
                                new CodeVariableElement(Set.of(PRIVATE, FINAL), context.getType(int.class), "operation"),
                                new CodeVariableElement(Set.of(PRIVATE), context.getType(Object.class), "data"),
                                new CodeVariableElement(Set.of(PRIVATE, FINAL), context.getType(int.class), "sequenceNumber"),
                                new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "childCount"),
                                new CodeVariableElement(Set.of(PRIVATE), generic(context.getDeclaredType(ArrayList.class), types.BytecodeLabel), "declaredLabels"));

                operationStackEntry.addAll(fields);

                operationStackEntry.add(createConstructorUsingFields(Set.of(), operationStackEntry, null));
                operationStackEntry.add(createAddDeclaredLabel());

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
        }

        class FinallyTryContextFactory {
            private CodeTypeElement create() {
                finallyTryContext.addAll(builderContextSensitiveState);
                finallyTryContext.addAll(List.of(
                                new CodeVariableElement(context.getType(int.class), "finallyTrySequenceNumber"),
                                new CodeVariableElement(generic(HashSet.class, context.getDeclaredType(Integer.class)), "finallyRelativeBranches"),
                                new CodeVariableElement(finallyTryContext.asType(), "parentContext")));

                finallyTryContext.add(createConstructorUsingFields(Set.of(), finallyTryContext, null));

                List<CodeVariableElement> handlerFields = new ArrayList<>(List.of(
                                new CodeVariableElement(context.getType(short[].class), "handlerBc"),
                                new CodeVariableElement(context.getType(int.class), "handlermaxStackHeight"),
                                new CodeVariableElement(context.getType(int[].class), "handlerSourceInfo"),
                                new CodeVariableElement(context.getType(int[].class), "handlerExHandlers"),
                                new CodeVariableElement(generic(HashMap.class, context.getDeclaredType(Integer.class), types.BytecodeLabel), "handlerUnresolvedBranchLabels"),
                                new CodeVariableElement(generic(HashMap.class, context.getDeclaredType(Integer.class), context.getDeclaredType(Integer.class)),
                                                "handlerUnresolvedBranchStackHeights")));
                if (model.enableTracing) {
                    handlerFields.add(new CodeVariableElement(context.getType(boolean[].class), "handlerBasicBlockBoundary"));
                }

                finallyTryContext.addAll(handlerFields);

                finallyTryContext.add(createSetHandler(handlerFields));
                finallyTryContext.add(createHandlerIsSet(handlerFields));

                return finallyTryContext;
            }

            private CodeExecutableElement createSetHandler(List<CodeVariableElement> handlerFields) {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), context.getType(void.class), "setHandler");
                CodeTreeBuilder b = ex.createBuilder();

                b.statement("assert !handlerIsSet()");
                for (CodeVariableElement field : handlerFields) {
                    String fieldName = field.getSimpleName().toString();
                    ex.addParameter(new CodeVariableElement(field.asType(), fieldName));
                    b.startStatement();
                    b.string("this.");
                    b.string(fieldName);
                    b.string(" = ");
                    b.string(fieldName);
                    b.end();
                }

                return ex;
            }

            private CodeExecutableElement createHandlerIsSet(List<CodeVariableElement> handlerFields) {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), context.getType(boolean.class), "handlerIsSet");
                CodeTreeBuilder b = ex.createBuilder();

                b.startReturn().variable(handlerFields.get(0)).string(" != null").end();

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
                constantPool.add(createGetConstant());
                constantPool.add(createToArray());

                return constantPool;
            }

            private CodeExecutableElement createAddConstant() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), context.getType(int.class),
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

            private CodeExecutableElement createGetConstant() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), context.getType(Object.class),
                                "getConstant");
                ex.addParameter(new CodeVariableElement(context.getType(int.class), "index"));
                CodeTreeBuilder b = ex.createBuilder();
                b.startReturn().string("constants.get(index)").end();

                return ex;
            }

            private CodeExecutableElement createToArray() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), new ArrayCodeTypeMirror(context.getType(Object.class)), "toArray");

                CodeTreeBuilder b = ex.createBuilder();
                b.startReturn().string("constants.toArray()").end();

                return ex;
            }
        }

        class BytecodeLocationFactory {
            private CodeTypeElement create() {
                List<CodeVariableElement> fields = List.of(
                                new CodeVariableElement(Set.of(PRIVATE, FINAL), context.getType(int.class), "bci"),
                                new CodeVariableElement(Set.of(PRIVATE, FINAL), context.getType(int.class), "sp"));

                bytecodeLocation.addAll(fields);

                CodeExecutableElement ctor = createConstructorUsingFields(Set.of(), bytecodeLocation, null);
                bytecodeLocation.add(ctor);

                return bytecodeLocation;
            }
        }

        class DeserializerContextImplFactory {
            private CodeTypeElement create() {
                deserializerContextImpl.setEnclosingElement(bytecodeNodeGen);
                deserializerContextImpl.getImplements().add(types.BytecodeDeserializer_DeserializerContext);

                deserializerContextImpl.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), generic(ArrayList.class, bytecodeNodeGen.asType()), "builtNodes"));
                deserializerContextImpl.add(createConstructorUsingFields(Set.of(PRIVATE), deserializerContextImpl));

                deserializerContextImpl.add(createReadBytecodeNode());

                return deserializerContextImpl;
            }

            private CodeExecutableElement createReadBytecodeNode() {
                CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeDeserializer_DeserializerContext, "readBytecodeNode");
                ex.renameArguments("buffer");
                CodeTreeBuilder b = ex.createBuilder();
                b.statement("return this.builtNodes.get(buffer.readChar())");
                return ex;
            }
        }

        private SerializationStateElements serializationElements;
        private CodeVariableElement serialization;

        private CodeTypeElement create() {
            addDoc(builder, true, """
                            Builder class to generate bytecode. An interpreter can invoke this class with its {@link com.oracle.truffle.api.bytecode.BytecodeParser} to generate bytecode.
                            """);

            builder.setSuperClass(types.BytecodeBuilder);
            builder.setEnclosingElement(bytecodeNodeGen);

            builder.add(uninitialized);
            uninitialized.createInitBuilder().string(-1).end();

            builder.add(new SavedStateFactory().create());
            builder.addAll(new OperationDataClassesFactory().create());
            builder.add(new OperationStackEntryFactory().create());
            builder.add(new FinallyTryContextFactory().create());
            builder.add(new ConstantPoolFactory().create());
            builder.add(new BytecodeLocationFactory().create());

            builder.add(createOperationNames());

            builder.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), bytecodeRootNodesImpl.asType(), "nodes"));
            builder.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), context.getType(boolean.class), "isReparse"));
            builder.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), context.getType(boolean.class), "parseBytecodes"));
            builder.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), context.getType(int.class), "tags"));
            builder.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), context.getType(int.class), "instrumentations"));
            builder.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), context.getType(boolean.class), "parseSources"));

            builder.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), generic(ArrayList.class, bytecodeNodeGen.asType()), "builtNodes"));

            builder.add(new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "buildIndex"));
            builder.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), generic(ArrayList.class, types.Source), "sources"));

            if (model.enableSerialization) {
                serializationElements = new SerializationStateElements();
                builder.add(serializationElements.serializationState);
                serialization = builder.add(new CodeVariableElement(Set.of(PRIVATE),
                                serializationElements.serializationState.asType(), "serialization"));
                builder.add(new DeserializerContextImplFactory().create());
            }

            builder.add(createReparseConstructor());
            builder.add(createParseConstructor());

            builder.add(createGetAllInstrumentations());
            builder.add(createGetAllTags());

            builder.addAll(builderState);

            builder.add(createCreateLocal());
            builder.add(createCreateLocalAllParameters());
            builder.add(createCreateLabel());
            builder.add(createRegisterUnresolvedLabel());
            builder.add(createResolveUnresolvedLabel());
            builder.add(createCreateBranchLabelMapping());
            builder.add(createCreateBranchStackHeightMapping());

            for (OperationModel operation : model.getOperations()) {
                if (operation.hasChildren()) {
                    builder.add(createBegin(operation));
                    builder.add(createEnd(operation));
                } else {
                    builder.add(createEmit(operation));
                }
            }

            builder.add(createBeginOperation());
            builder.add(createEndOperation());
            builder.add(createEmitOperationBegin());
            builder.add(createBeforeChild());
            builder.add(createAfterChild());
            builder.add(createDoEmitFinallyHandler());
            builder.add(createEnsureBytecodeCapacity());
            builder.add(createDoEmitVariadic());
            builder.add(createDoCreateExceptionHandler());
            builder.add(createDoEmitSourceInfo());
            builder.add(createFinish());
            builder.add(createDoEmitLeaves());
            builder.add(createAllocateNode());
            builder.add(createAllocateBranchProfile());
            builder.add(createInFinallyTryHandler());
            builder.add(createGetFinallyTryHandlerSequenceNumber());
            if (model.enableSerialization) {
                builder.add(createSerialize());
                builder.add(createDeserialize());
            }

            builder.addAll(doEmitInstructionMethods.values());

            return builder;
        }

        private CodeExecutableElement createSerialize() {
            CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE),
                            context.getType(void.class), "serialize");
            method.addParameter(new CodeVariableElement(type(DataOutput.class), "buffer"));
            method.addParameter(new CodeVariableElement(types.BytecodeSerializer, "callback"));
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

                b.lineComment("2. Serialize the fields stored on each root node.");
                b.statement("short[][] nodeFields = new short[builtNodes.size()][]");
                b.startFor().string("int i = 0; i < nodeFields.length; i ++").end().startBlock();
                b.declaration(bytecodeNodeGen.asType(), "node", "builtNodes.get(i)");
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

        private CodeExecutableElement createDeserialize() {
            CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE),
                            context.getType(void.class), "deserialize");
            mergeSuppressWarnings(method, "hiding");

            method.addParameter(new CodeVariableElement(types.TruffleLanguage, "language"));
            method.addParameter(new CodeVariableElement(generic(Supplier.class, DataInput.class), "bufferSupplier"));
            method.addParameter(new CodeVariableElement(types.BytecodeDeserializer, "callback"));

            CodeTreeBuilder b = method.createBuilder();

            b.startTryBlock();

            b.statement("ArrayList<Object> consts = new ArrayList<>()");
            b.statement("ArrayList<BytecodeLocal> locals = new ArrayList<>()");
            b.statement("ArrayList<BytecodeLabel> labels = new ArrayList<>()");
            b.declaration(type(DataInput.class), "buffer", "bufferSupplier.get()");
            b.declaration(generic(context.getDeclaredType(ArrayList.class), bytecodeNodeGen.asType()), "builtNodes", "new ArrayList<>()");

            b.startStatement();
            b.type(types.BytecodeDeserializer_DeserializerContext);
            b.string(" context = ").startNew(deserializerContextImpl.getSimpleName().toString()).string("builtNodes").end();
            b.end();

            b.startWhile().string("true").end().startBlock();

            b.declaration(type(short.class), "code", "buffer.readShort()");

            b.startSwitch().string("code").end().startBlock();

            b.startCase().staticReference(serializationElements.codeCreateLabel).end().startBlock();
            b.statement("labels.add(createLabel())");
            b.statement("break");
            b.end();

            b.startCase().staticReference(serializationElements.codeCreateLocal).end().startBlock();
            b.statement("locals.add(createLocal())");
            b.statement("break");
            b.end();

            b.startCase().staticReference(serializationElements.codeCreateObject).end().startBlock();
            b.statement("consts.add(callback.deserialize(context, buffer))");
            b.statement("break");
            b.end();

            b.startCase().staticReference(serializationElements.codeEndSerialize).end().startBlock();

            if (model.serializedFields.size() != 0) {
                b.startFor().string("int i = 0; i < builtNodes.size(); i++").end().startBlock();
                b.declaration(bytecodeNodeGen.asType(), "node", "builtNodes.get(i)");
                for (int i = 0; i < model.serializedFields.size(); i++) {
                    VariableElement var = model.serializedFields.get(i);
                    b.startStatement();
                    b.string("node.").string(var.getSimpleName().toString());
                    b.string(" = ");
                    if (ElementUtils.needsCastTo(type(Object.class), var.asType())) {
                        b.cast(var.asType());
                    }
                    b.string("consts.get(buffer.readShort())");
                    b.end();
                }
                b.end();
            }

            b.returnStatement();
            b.end();

            final boolean hasTags = !model.getProvidedTags().isEmpty();
            for (OperationModel operation : model.getOperations()) {

                // create begin/emit code
                b.startCase().staticReference(serializationElements.codeBegin[operation.id]).end().startBlock();

                if (operation.kind == OperationKind.INSTRUMENT_TAG && !hasTags) {
                    b.startThrow().startNew(context.getType(IllegalStateException.class));
                    b.doubleQuote(String.format("Cannot deserialize instrument tag. The language does not specify any tags with a @%s annotation.",
                                    ElementUtils.getSimpleName(types.ProvidedTags)));
                    b.end().end();
                    b.end(); // switch block
                    continue;
                }

                for (int i = 0; i < operation.operationArguments.length; i++) {
                    TypeMirror argType = operation.operationArguments[i].type();
                    String argumentName = operation.getOperationArgumentName(i);
                    if (ElementUtils.typeEquals(argType, types.TruffleLanguage)) {
                        continue; // language is already available as a parameter
                    } else if (ElementUtils.typeEquals(argType, types.BytecodeLocal)) {
                        b.statement("BytecodeLocal ", argumentName, " = locals.get(buffer.readShort())");
                    } else if (ElementUtils.typeEquals(argType, new ArrayCodeTypeMirror(types.BytecodeLocal))) {
                        b.statement("BytecodeLocal[] ", argumentName, " = new BytecodeLocal[buffer.readShort()]");
                        b.startFor().string("int i = 0; i < ", argumentName, ".length; i++").end().startBlock();
                        // this can be optimized since they are consecutive
                        b.statement(argumentName, "[i] = locals.get(buffer.readShort())");
                        b.end();
                    } else if (ElementUtils.typeEquals(argType, types.BytecodeLabel)) {
                        b.statement("BytecodeLabel ", argumentName, " = labels.get(buffer.readShort())");
                    } else if (ElementUtils.typeEquals(argType, context.getType(int.class))) {
                        b.statement("int ", argumentName, " = buffer.readInt()");
                    } else if (operation.kind == OperationKind.INSTRUMENT_TAG && i == 0) {
                        b.startStatement().type(argType).string(" ", argumentName, " = TAG_MASK_TO_TAGS.computeIfAbsent(buffer.readInt(), (v) -> mapTagMaskToTagsArray(v))").end();
                    } else if (ElementUtils.isObject(argType) || ElementUtils.typeEquals(argType, types.Source)) {
                        b.startStatement().type(argType).string(" ", argumentName, " = ");
                        if (!ElementUtils.isObject(argType)) {
                            b.cast(argType);
                        }
                        b.string("consts.get(buffer.readShort())").end();
                    } else {
                        throw new UnsupportedOperationException("cannot deserialize: " + argType);
                    }
                }

                b.startStatement();
                if (operation.hasChildren()) {
                    b.startCall("begin" + operation.name);
                } else {
                    b.startCall("emit" + operation.name);
                }

                for (int i = 0; i < operation.operationArguments.length; i++) {
                    b.string(operation.getOperationArgumentName(i));
                }

                b.end(2); // statement, call

                b.statement("break");

                b.end(); // case block

                if (operation.hasChildren()) {
                    b.startCase().staticReference(serializationElements.codeEnd[operation.id]).end().startBlock();

                    if (operation.kind == OperationKind.ROOT) {
                        b.startStatement();
                        b.type(model.getTemplateType().asType()).string(" node = ").string("end" + operation.name + "()");
                        b.end();
                        b.startStatement().startCall("builtNodes.add").startGroup().cast(bytecodeNodeGen.asType()).string("node").end().end().end();
                    } else {
                        b.statement("end", operation.name, "()");
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

        private CodeExecutableElement createFinish() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "finish");
            CodeTreeBuilder b = ex.createBuilder();

            b.startIf().string("!isReparse").end().startBlock();
            b.startStatement().string("nodes.setNodes(builtNodes.toArray(new ").type(bytecodeNodeGen.asType()).string("[0]))").end();
            b.end();

            return ex;
        }

        private CodeExecutableElement createCreateLocal() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), types.BytecodeLocal, "createLocal");

            addDoc(ex, true, "Creates a new local. Uses default values for the local's slot metadata.");

            CodeTreeBuilder b = ex.createBuilder();

            b.startReturn().startCall("createLocal");
            b.staticReference(types.FrameSlotKind, "Illegal");
            b.string("null"); // name
            b.string("null"); // info
            b.end(2);

            return ex;
        }

        private CodeExecutableElement createCreateLocalAllParameters() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), types.BytecodeLocal, "createLocal");
            ex.addParameter(new CodeVariableElement(types.FrameSlotKind, "slotKind"));
            ex.addParameter(new CodeVariableElement(context.getDeclaredType(Object.class), "name"));
            ex.addParameter(new CodeVariableElement(context.getDeclaredType(Object.class), "info"));

            addDoc(ex, true, """
                            Creates a new local. Uses the given {@code slotKind}, {@code name}, and {@code info} for its frame slot metadata.

                            @param slotKind the slot kind of the local.
                            @param name the name assigned to the local's slot.
                            @param info the info assigned to the local's slot.
                            """);

            CodeTreeBuilder b = ex.createBuilder();

            if (model.enableSerialization) {
                b.startIf().string("serialization != null").end().startBlock();
                serializationWrapException(b, () -> {
                    serializationElements.writeShort(b, serializationElements.codeCreateLocal);
                    // TODO: serialize slot, name, info
                });
                b.startReturn().startNew(bytecodeLocalImpl.asType()).string("numLocals++").end(2);
                b.end();
            }

            b.startStatement().startCall("frameDescriptorBuilder.addSlot");
            b.string("slotKind");
            b.string("name");
            b.string("info");
            b.end(2);

            b.startReturn().startNew(bytecodeLocalImpl.asType()).string("numLocals++").end(2);

            return ex;
        }

        private CodeExecutableElement createCreateLabel() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), types.BytecodeLabel, "createLabel");

            addDoc(ex, true, "Creates a new label. The result should be {@link #emitLabel emitted} and can be {@link #emitBranch branched to}.");

            CodeTreeBuilder b = ex.createBuilder();

            if (model.enableSerialization) {
                b.startIf().string("serialization != null").end().startBlock();
                serializationWrapException(b, () -> {
                    serializationElements.writeShort(b, serializationElements.codeCreateLabel);
                });

                b.startReturn().startNew(bytecodeLabelImpl.asType());
                b.string("numLabels++");
                b.string(UNINIT);
                b.string(serialization.getName(), ".", serializationElements.labelCount.getName(), "++");
                b.string("0");
                b.end(2);
                b.end();
            }

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
            b.string("finallyTryContext == null ? " + UNINIT + " : finallyTryContext.finallyTrySequenceNumber");
            b.end(2);

            b.statement("operationStack[operationSp - 1].addDeclaredLabel(result)");

            b.startReturn().string("result").end();

            return ex;
        }

        private CodeExecutableElement createRegisterUnresolvedLabel() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "registerUnresolvedLabel");
            ex.addParameter(new CodeVariableElement(types.BytecodeLabel, "label"));
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "immediateBci"));
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "stackHeight"));

            CodeTreeBuilder b = ex.createBuilder();
            b.declaration(generic(context.getDeclaredType(ArrayList.class), bytecodeLocation.asType()), "locations", "unresolvedLabels.computeIfAbsent(label, k -> new ArrayList<>())");
            b.statement("locations.add(new BytecodeLocation(immediateBci, stackHeight))");

            return ex;
        }

        private CodeExecutableElement createResolveUnresolvedLabel() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "resolveUnresolvedLabel");
            ex.addParameter(new CodeVariableElement(types.BytecodeLabel, "label"));
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "stackHeight"));

            CodeTreeBuilder b = ex.createBuilder();

            b.statement("BytecodeLabelImpl impl = (BytecodeLabelImpl) label");
            b.statement("assert impl.isDefined()");
            b.declaration(generic(List.class, bytecodeLocation.asType()), "sites", "unresolvedLabels.remove(impl)");
            b.startIf().string("sites != null").end().startBlock();
            b.startFor().startGroup().type(bytecodeLocation.asType()).string(" site : sites").end(2).startBlock();

            b.startIf().string("stackHeight != site.sp").end().startBlock();
            emitThrowIllegalStateException(b, "\"BytecodeLabel was emitted at a position with a different stack height than a branch instruction that targets it. Branches must be balanced.\"");
            b.end();
            b.statement(writeBc("site.bci", "(short) impl.bci"));
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
            b.startFor().startGroup().type(bytecodeLocation.asType()).string(" site : unresolvedLabels.get(lbl)").end(2).startBlock();
            b.statement("assert !result.containsKey(site.bci)");
            b.statement("result.put(site.bci, lbl)");
            b.end(2);
            b.startReturn().string("result").end();

            return ex;
        }

        private CodeExecutableElement createCreateBranchStackHeightMapping() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), generic(HashMap.class, context.getDeclaredType(Integer.class), context.getDeclaredType(Integer.class)),
                            "createBranchStackHeightMapping");
            ex.addParameter(new CodeVariableElement(unresolvedLabelsType, "unresolvedLabels"));

            CodeTreeBuilder b = ex.createBuilder();
            b.statement("HashMap<Integer, Integer> result = new HashMap<>()");
            b.startFor().string("BytecodeLabel lbl : unresolvedLabels.keySet()").end().startBlock();
            b.startFor().startGroup().type(bytecodeLocation.asType()).string(" site : unresolvedLabels.get(lbl)").end(2).startBlock();
            b.statement("assert !result.containsKey(site.bci)");
            b.statement("result.put(site.bci, site.sp)");
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

            createCheckRoot(b);

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
            b.string("0");
            b.string("null");
            b.end(2);

            return ex;
        }

        private static void appendOperationDescriptionForDoc(StringBuilder sb, OperationModel operation) {
            if (operation.isCustom()) {
                sb.append("custom ");
                switch (operation.kind) {
                    case CUSTOM_SIMPLE:
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
                sb.append("built-in ");
                sb.append(operation.name);
            }
            sb.append(" operation.");
        }

        private static void addBeginOrEmitOperationDoc(OperationModel operation, CodeExecutableElement ex) {
            StringBuilder sb = new StringBuilder();
            if (operation.hasChildren()) {
                sb.append("Begins a ");
            } else {
                sb.append("Emits a ");
            }

            appendOperationDescriptionForDoc(sb, operation);

            if (operation.hasChildren()) {
                sb.append(" A corresponding call to {@link #end");
                sb.append(operation.name);
                sb.append("} is required to end the operation.");
            }

            List<String> javadoc = new ArrayList<>(1);
            javadoc.add(sb.toString());

            if (operation.operationArguments.length != 0) {
                javadoc.add("");
                for (OperationArgument argument : operation.operationArguments) {
                    javadoc.add(argument.toJavadocParam());
                }
            }

            addDoc(ex, true, javadoc);
        }

        private void addEndOperationDoc(OperationModel operation, CodeExecutableElement ex) {
            assert operation.hasChildren();

            StringBuilder sb = new StringBuilder("Ends a ");

            appendOperationDescriptionForDoc(sb, operation);

            addDoc(ex, true, sb.toString());
        }

        private CodeExecutableElement createBegin(OperationModel operation) {
            if (operation.kind == OperationKind.ROOT) {
                return createBeginRoot(operation);
            }
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), context.getType(void.class), "begin" + operation.name);

            for (OperationArgument arg : operation.operationArguments) {
                ex.addParameter(arg.toVariableElement());
            }
            ex.setVarArgs(operation.operationArgumentVarArgs);
            addBeginOrEmitOperationDoc(operation, ex);

            CodeTreeBuilder b = ex.createBuilder();

            if (operation.kind == OperationKind.INSTRUMENT_TAG) {
                if (model.getProvidedTags().isEmpty()) {
                    b.startThrow().startNew(context.getType(IllegalArgumentException.class));
                    b.doubleQuote(String.format("Given tag is not provided by the language. Add a @%s annotation to the %s class.",
                                    ElementUtils.getSimpleName(types.ProvidedTags), ElementUtils.getSimpleName(model.languageClass)));
                    b.end().end();
                    return ex;
                }
            }

            if (operation.isSourceOnly()) {
                b.startIf().string("!parseSources").end().startBlock();
                b.returnStatement();
                b.end();
            }

            if (model.enableSerialization) {
                b.startIf().string("serialization != null").end().startBlock();
                createSerializeBegin(operation, b);
                b.statement("return");
                b.end();
            }

            b.startStatement().startCall("beforeChild").end(2);

            // NB: This method may emit code that declares variables referenced by the resultant
            // tree. Thus, we have to call it before we start the beginOperation call.
            CodeTree operationData = createOperationBeginData(b, operation);
            b.startStatement().startCall("beginOperation");
            b.tree(createOperationConstant(operation));
            b.tree(operationData);
            b.end(2);

            switch (operation.kind) {
                case SOURCE:
                    b.startIf().string(operation.getOperationArgumentName(0) + ".hasBytes()").end().startBlock();
                    b.startThrow().startNew(type(IllegalArgumentException.class)).doubleQuote("Byte-based sources are not supported.").end(2);
                    b.end();

                    b.startIf().string("sourceIndexStack.length == sourceIndexSp").end().startBlock();
                    b.statement("sourceIndexStack = Arrays.copyOf(sourceIndexStack, sourceIndexSp * 2)");
                    b.end();

                    b.statement("int index = sources.indexOf(" + operation.getOperationArgumentName(0) + ")");
                    b.startIf().string("index == -1").end().startBlock();
                    b.statement("index = sources.size()");
                    b.statement("sources.add(" + operation.getOperationArgumentName(0) + ")");
                    b.end();

                    b.statement("sourceIndexStack[sourceIndexSp++] = index");

                    b.startIf().string("sourceLocationStack.length == sourceLocationSp").end().startBlock();
                    b.statement("sourceLocationStack = Arrays.copyOf(sourceLocationStack, sourceLocationSp * 2)");
                    b.end();

                    b.statement("sourceLocationStack[sourceLocationSp++] = -1");
                    b.statement("sourceLocationStack[sourceLocationSp++] = -1");

                    b.statement("doEmitSourceInfo(index, -1, -1)");

                    break;
                case SOURCE_SECTION:
                    b.startIf().string("sourceIndexSp == 0").end().startBlock();
                    emitThrowIllegalStateException(b, "\"No enclosing Source operation found - each SourceSection must be enclosed in a Source operation.\"");
                    b.end();

                    b.startIf().string("sourceLocationStack.length == sourceLocationSp").end().startBlock();
                    b.statement("sourceLocationStack = Arrays.copyOf(sourceLocationStack, sourceLocationSp * 2)");
                    b.end();

                    b.statement("sourceLocationStack[sourceLocationSp++] = " + operation.getOperationArgumentName(0));
                    b.statement("sourceLocationStack[sourceLocationSp++] = " + operation.getOperationArgumentName(1));

                    b.statement("doEmitSourceInfo(sourceIndexStack[sourceIndexSp - 1], " + operation.getOperationArgumentName(0) + ", " + operation.getOperationArgumentName(1) + ")");
                    break;
                case WHILE:
                    if (model.enableTracing) {
                        b.statement("basicBlockBoundary[bci] = true");
                    }
                    break;
                case FINALLY_TRY:
                case FINALLY_TRY_NO_EXCEPT:
                    buildContextSensitiveFieldInitializer(b);
                    if (model.enableTracing) {
                        b.statement("basicBlockBoundary[0] = true");
                    }
                    break;
            }

            return ex;
        }

        private CodeExecutableElement createBeginRoot(OperationModel rootOperation) {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), context.getType(void.class), "beginRoot");
            ex.addParameter(new CodeVariableElement(types.TruffleLanguage, "language"));

            addDoc(ex, true, """
                            Begins a new root node.

                            This method should always be invoked before subsequent builder methods, which generate and validate bytecode for the root node. The resultant root node is returned by {@link #endRoot}.

                            This method can be called while generating another root node. Bytecode generation for the first root node suspends until generation for the second root node finishes (the second is not "nested" in the first).

                            @param language the Truffle language to associate with the root node.
                            """);

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
            buildContextSensitiveFieldInitializer(b);
            b.startAssign("frameDescriptorBuilder").tree(CodeTreeBuilder.createBuilder().startStaticCall(types.FrameDescriptor, "newBuilder").end().build()).end();
            if (userLocalsStartIndex > 0) {
                // Allocate reserved slots
                b.startStatement().startCall("frameDescriptorBuilder.addSlots");
                b.string(USER_LOCALS_START_IDX);
                b.staticReference(types.FrameSlotKind, "Illegal");
                b.end(2);
            }

            b.statement("operationStack = new OperationStackEntry[8]");
            b.statement("operationSequenceNumber = 0");
            b.statement("operationSp = 0");

            b.statement("numLocals = " + USER_LOCALS_START_IDX);
            b.statement("numLabels = 0");
            b.statement("numNodes = 0");
            b.statement("numConditionalBranches = 0");
            b.statement("constantPool = new ConstantPool()");

            b.startIf().string("parseSources").end().startBlock();
            b.statement("sourceIndexStack = new int[1]");
            b.statement("sourceIndexSp = 0");
            b.statement("sourceLocationStack = new int[12]");
            b.statement("sourceLocationSp = 0");
            b.end();

            b.startStatement().startCall("beginOperation");
            b.tree(createOperationConstant(rootOperation));
            b.tree(createOperationData("RootData", "language"));
            b.end(2);

            return ex;
        }

        private void createSerializeBegin(OperationModel operation, CodeTreeBuilder b) {
            serializationWrapException(b, () -> {

                CodeTreeBuilder after = CodeTreeBuilder.createBuilder();
                for (int i = 0; i < operation.operationArguments.length; i++) {
                    TypeMirror argType = operation.operationArguments[i].type();
                    String argumentName = operation.getOperationArgumentName(i);
                    if (ElementUtils.typeEquals(argType, types.TruffleLanguage)) {
                        b.statement("serialization.language = language");
                    } else if (ElementUtils.typeEquals(argType, types.BytecodeLocal)) {
                        serializationElements.writeShort(after, "(short) ((BytecodeLocalImpl) " + argumentName + ").index");
                    } else if (ElementUtils.typeEquals(argType, new ArrayCodeTypeMirror(types.BytecodeLocal))) {
                        serializationElements.writeShort(after, "(short) " + argumentName + ".length");
                        after.startFor().string("int i = 0; i < " + argumentName + ".length; i++").end().startBlock();
                        serializationElements.writeShort(after, "(short) ((BytecodeLocalImpl) " + argumentName + "[i]).index");
                        after.end();
                    } else if (ElementUtils.typeEquals(argType, types.BytecodeLabel)) {
                        serializationElements.writeShort(after, "(short) ((BytecodeLabelImpl) " + argumentName + ").declaringOp");
                    } else if (ElementUtils.typeEquals(argType, context.getType(int.class))) {
                        serializationElements.writeInt(after, argumentName);
                    } else if (operation.kind == OperationKind.INSTRUMENT_TAG && i == 0) {
                        serializationElements.writeInt(after, "encodeTags(" + operation.getOperationArgumentName(0) + ")");
                    } else if (ElementUtils.isObject(argType) || ElementUtils.typeEquals(argType, types.Source)) {
                        String index = argumentName + "_index";
                        b.statement("short ", index, " = ", "serialization.serializeObject(", argumentName, ")");
                        serializationElements.writeShort(after, index);
                    } else {
                        throw new UnsupportedOperationException("cannot serialize: " + argType);
                    }
                }
                serializationElements.writeShort(b, serializationElements.codeBegin[operation.id]);

                b.tree(after.build());
            });
        }

        private CodeTree createOperationBeginData(CodeTreeBuilder b, OperationModel operation) {
            if (operation.isTransparent) {
                return createOperationData("TransparentOperationData", "false", UNINIT);
            }
            return switch (operation.kind) {
                case STORE_LOCAL, STORE_LOCAL_MATERIALIZED -> {
                    if (model.usesBoxingElimination()) {
                        yield createOperationData("StoreLocalData", "(BytecodeLocalImpl)" + operation.getOperationArgumentName(0), UNINIT);
                    } else {
                        yield CodeTreeBuilder.singleString(operation.getOperationArgumentName(0));
                    }
                }
                case LOAD_LOCAL_MATERIALIZED -> {
                    yield CodeTreeBuilder.singleString(operation.getOperationArgumentName(0));
                }
                case IF_THEN -> createOperationData("IfThenData", UNINIT);
                case IF_THEN_ELSE -> createOperationData("IfThenElseData", UNINIT, UNINIT);
                case CONDITIONAL -> {
                    if (model.usesBoxingElimination()) {
                        yield createOperationData("ConditionalData", UNINIT, UNINIT, UNINIT, UNINIT);
                    } else {
                        yield createOperationData("ConditionalData", UNINIT, UNINIT);
                    }
                }
                case WHILE -> createOperationData("WhileData", "bci", UNINIT);
                case TRY_CATCH -> createOperationData("TryCatchData", "bci",
                                "currentStackHeight",
                                "((BytecodeLocalImpl) " + operation.getOperationArgumentName(0) + ").index",
                                UNINIT,
                                UNINIT,
                                UNINIT);
                case FINALLY_TRY, FINALLY_TRY_NO_EXCEPT -> {
                    // set finallyTryContext inside createFinallyTry before calling beginOperation
                    b.startAssign("finallyTryContext").startNew(finallyTryContext.asType());
                    for (CodeVariableElement field : builderContextSensitiveState) {
                        b.string(field.getName());
                    }
                    b.string("operationSequenceNumber");
                    b.string("new HashSet<>()");
                    b.string("finallyTryContext");
                    b.end(2);

                    String arg1 = (operation.kind == OperationKind.FINALLY_TRY) ? operation.getOperationArgumentName(0) : "null";
                    yield createOperationData("FinallyTryData", arg1, "finallyTryContext");
                }
                case CUSTOM_SIMPLE -> {
                    CodeTreeBuilder childBciArrayBuilder = CodeTreeBuilder.createBuilder();
                    childBciArrayBuilder.startNewArray(arrayOf(context.getType(int.class)), null);
                    for (InstructionImmediate immediate : operation.instruction.getImmediates()) {
                        if (immediate.kind() == ImmediateKind.BYTECODE_INDEX) {
                            childBciArrayBuilder.string(UNINIT);
                        }
                    }
                    childBciArrayBuilder.end();

                    String[] args = new String[operation.operationArguments.length + 1];
                    args[0] = childBciArrayBuilder.toString();
                    for (int i = 0; i < operation.operationArguments.length; i++) {
                        if (operation.operationArguments[i].type().getKind() == TypeKind.ARRAY) {
                            // cast to Object to avoid Java interpreting the array as desugared
                            // varargs
                            args[i + 1] = "(Object) " + operation.getOperationArgumentName(i);
                        } else {
                            args[i + 1] = operation.getOperationArgumentName(i);
                        }
                    }
                    yield createOperationData("CustomOperationData", args);
                }
                case CUSTOM_SHORT_CIRCUIT -> {
                    if (model.isBoxingEliminated(type(boolean.class))) {
                        yield createOperationData("CustomShortCircuitOperationData", UNINIT);
                    } else {
                        yield createOperationData("CustomShortCircuitOperationData");
                    }
                }
                default -> CodeTreeBuilder.singleString("null");
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
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "endOperation");
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "id"));

            CodeTreeBuilder b = ex.createBuilder();

            b.startIf().string("operationSp <= 0 || operationStack == null").end().startBlock(); // {
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

            b.statement("operationSp -= 1");

            return ex;
        }

        private CodeExecutableElement createEnd(OperationModel operation) {
            if (operation.kind == OperationKind.ROOT) {
                // endRoot is handled specially.
                return createEndRoot(operation);
            }

            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), context.getType(void.class), "end" + operation.name);
            addEndOperationDoc(operation, ex);
            CodeTreeBuilder b = ex.createBuilder();

            if (model.enableSerialization) {
                b.startIf().string("serialization != null").end().startBlock();
                serializationWrapException(b, () -> {
                    serializationElements.writeShort(b, serializationElements.codeEnd[operation.id]);
                    b.statement("return");
                });
                b.end();
            }

            if (operation.isSourceOnly()) {
                b.startIf().string("!parseSources").end().startBlock();
                b.returnStatement();
                b.end();
            }

            b.startStatement().startCall("endOperation");
            b.tree(createOperationConstant(operation));
            b.end(2);

            if (operation.kind == OperationKind.CUSTOM_SHORT_CIRCUIT) {
                // Short-circuiting operations should have at least one child.
                b.startIf().string("operationStack[operationSp].childCount == 0").end().startBlock();
                emitThrowIllegalStateException(b, "\"Operation " + operation.name + " expected at least " + childString(1) +
                                ", but \" + operationStack[operationSp].childCount + \" provided. This is probably a bug in the parser.\"");
                b.end();
            } else if (operation.isVariadic && operation.numChildren > 1) {
                // The variadic child is included in numChildren, so the operation requires
                // numChildren - 1 children at minimum.
                b.startIf().string("operationStack[operationSp].childCount < " + (operation.numChildren - 1)).end().startBlock();
                emitThrowIllegalStateException(b, "\"Operation " + operation.name + " expected at least " + childString(operation.numChildren - 1) +
                                ", but \" + operationStack[operationSp].childCount + \" provided. This is probably a bug in the parser.\"");
                b.end();
            } else if (!operation.isVariadic) {
                b.startIf().string("operationStack[operationSp].childCount != " + operation.numChildren).end().startBlock();
                emitThrowIllegalStateException(b, "\"Operation " + operation.name + " expected exactly " + childString(operation.numChildren) +
                                ", but \" + operationStack[operationSp].childCount + \" provided. This is probably a bug in the parser.\"");
                b.end();
            }

            switch (operation.kind) {
                case CUSTOM_SHORT_CIRCUIT:
                    InstructionModel shortCircuitInstruction = operation.instruction;
                    emitCastOperationData(b, "CustomShortCircuitOperationData", "operationSp");
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
                    b.statement("sourceLocationSp -= 2");
                    b.startStatement().startCall("doEmitSourceInfo");
                    b.string("sourceIndexStack[sourceIndexSp - 1]");
                    b.string("sourceLocationStack[sourceLocationSp - 2]");
                    b.string("sourceLocationStack[sourceLocationSp - 1]");
                    b.end(2);
                    break;
                case SOURCE:
                    b.statement("sourceLocationSp -= 2");
                    b.statement("sourceIndexSp -= 1");
                    b.startIf().string("sourceIndexSp > 0").end().startBlock();
                    b.statement("doEmitSourceInfo(sourceIndexStack[sourceIndexSp - 1], sourceLocationStack[sourceLocationSp - 2], sourceLocationStack[sourceLocationSp - 1])");
                    b.end().startElseBlock();
                    b.statement("doEmitSourceInfo(sourceIndexStack[sourceIndexSp], -1, -1)");
                    b.end();
                    break;
                case IF_THEN:
                case IF_THEN_ELSE:
                case WHILE:
                    if (model.enableTracing) {
                        b.statement("basicBlockBoundary[bci] = true");
                    }
                    break;
                case CONDITIONAL:
                    emitCastOperationData(b, "ConditionalData", "operationSp");
                    if (model.enableTracing) {
                        b.statement("basicBlockBoundary[bci] = true");
                    }
                    if (model.usesBoxingElimination()) {
                        buildEmitInstruction(b, operation.instruction, emitMergeConditionalArguments(operation.instruction));
                    }
                    break;
                case FINALLY_TRY:
                    emitCastOperationData(b, "FinallyTryData", "operationSp");
                    b.statement("BytecodeLocalImpl exceptionLocal = (BytecodeLocalImpl) operationData.exceptionLocal");
                    b.statement("FinallyTryContext ctx = (FinallyTryContext) operationData.finallyTryContext");
                    b.statement("short exceptionIndex = (short) exceptionLocal.index");
                    b.statement("int exHandlerIndex = doCreateExceptionHandler(ctx.bci, bci, " + UNINIT + " /* handler start */, currentStackHeight, exceptionIndex)");
                    b.lineComment("emit handler for normal completion case");
                    b.statement("doEmitFinallyHandler(ctx)");
                    b.statement("int endBranchIndex = bci + 1");
                    /**
                     * Skip over the catch handler. This branch could be relative if the current
                     * FinallyTry is nested in another FinallyTry handler.
                     *
                     * (NB: By the time we invoke endFinallyTry, our finallyTryContext has been
                     * restored to point to the parent context.)
                     */
                    emitFinallyRelativeBranchCheck(b, 1);
                    buildEmitInstruction(b, model.branchInstruction, new String[]{UNINIT});

                    b.lineComment("emit handler for exceptional case");
                    b.statement("exHandlers[exHandlerIndex + 2] = bci /* handler start */");
                    b.statement("doEmitFinallyHandler(ctx)");
                    buildEmitInstruction(b, model.throwInstruction, new String[]{"exceptionIndex"});
                    b.statement(writeBc("endBranchIndex", "(short) bci"));
                    break;
                case FINALLY_TRY_NO_EXCEPT:
                    emitCastOperationData(b, "FinallyTryData", "operationSp");
                    b.statement("BytecodeLocalImpl exceptionLocal = (BytecodeLocalImpl) operationData.exceptionLocal");
                    b.statement("FinallyTryContext ctx = (FinallyTryContext) operationData.finallyTryContext");
                    b.statement("doEmitFinallyHandler(ctx)");
                    break;
                case RETURN:
                    b.statement("doEmitLeaves(-1)");
                    buildEmitOperationInstruction(b, operation);
                    break;
                default:
                    if (operation.instruction != null) {
                        buildEmitOperationInstruction(b, operation);
                    }
                    break;
            }

            if (operation.isTransparent) {
                emitCastOperationData(b, "TransparentOperationData", "operationSp");
                b.startStatement().startCall("afterChild");
                b.string("operationData.producedValue");
                b.string("operationData.childBci");
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

            return ex;
        }

        private CodeExecutableElement createEndRoot(OperationModel rootOperation) {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), model.templateType.asType(), "endRoot");

            addDoc(ex, true, """
                            Finishes generating bytecode for the current root node.

                            @returns the root node with generated bytecode.
                            """);

            CodeTreeBuilder b = ex.getBuilder();

            if (model.enableSerialization) {
                b.startIf().string("serialization != null").end().startBlock();
                serializationWrapException(b, () -> {
                    CodeTreeBuilder constructorCallBuilder = CodeTreeBuilder.createBuilder();
                    constructorCallBuilder.startNew(bytecodeNodeGen.asType());
                    constructorCallBuilder.string("serialization.language");
                    constructorCallBuilder.startStaticCall(types.FrameDescriptor, "newBuilder").end();
                    constructorCallBuilder.string("null"); // BytecodeRootNodesImpl
                    constructorCallBuilder.string("null"); // bc
                    constructorCallBuilder.string("null"); // constants
                    constructorCallBuilder.string("null"); // handlers
                    constructorCallBuilder.string("null"); // sourceInfo
                    constructorCallBuilder.string("null"); // sources
                    constructorCallBuilder.string("0"); // numLocals
                    constructorCallBuilder.string("0"); // numNodes
                    constructorCallBuilder.string("buildIndex++");
                    constructorCallBuilder.end();
                    b.declaration(bytecodeNodeGen.asType(), "node", constructorCallBuilder.build());

                    serializationElements.writeShort(b, serializationElements.codeEnd[rootOperation.id]);
                    b.statement("builtNodes.add(node)");
                    b.statement("return node");
                });
                b.end();
            }

            b.startStatement().startCall("endOperation");
            b.tree(createOperationConstant(rootOperation));
            b.end(2);

            b.declaration(bytecodeNodeGen.asType(), "result", (CodeTree) null);
            b.startIf().string("isReparse").end().startBlock(); // {
            b.statement("result = builtNodes.get(buildIndex)");

            b.startIf().string("parseSources").end().startBlock();
            CodeTree copyOf = CodeTreeBuilder.createBuilder().startStaticCall(type(Arrays.class), "copyOf").string("sourceInfo").string("sourceInfoIndex").end().build();
            b.declaration(arrayOf(context.getType(int.class)), "sourceInfoArray", copyOf);
            b.statement("result.addSource(sourceInfoArray, sources)");
            b.end();

            b.startAssert().string("result.buildIndex == buildIndex").end();

            b.end().startElseBlock(); // } {

            emitCastOperationData(b, "RootData", "operationSp");
            b.declaration(types.TruffleLanguage, "language", "operationData.language");

            b.startIf().string("operationData.mayFallThrough").end().startBlock();
            buildEmitInstruction(b, model.trapInstruction);
            b.end();

            // Allocate stack space in the frame descriptor.
            b.startStatement().startCall("frameDescriptorBuilder.addSlots");
            b.string("maxStackHeight");
            b.staticReference(types.FrameSlotKind, "Illegal");
            b.end(2);

            b.declaration(arrayOf(context.getType(int.class)), "sourceInfoArray", "null");
            b.startIf().string("parseSources").end().startBlock();
            b.startAssign("sourceInfoArray").startStaticCall(type(Arrays.class), "copyOf").string("sourceInfo").string("sourceInfoIndex").end().end();
            b.end();

            b.startAssign("result").startNew(bytecodeNodeGen.asType());
            b.string("language");
            b.string("frameDescriptorBuilder");
            b.string("nodes"); // BytecodeRootNodesImpl
            b.string("Arrays.copyOf(bc, bci)"); // bc
            b.string("constantPool.toArray()"); // constants
            b.string("Arrays.copyOf(exHandlers, exHandlerCount)"); // handlers
            b.string("sourceInfoArray");
            b.string("sources");
            b.string("numLocals");
            b.string("numNodes");
            b.string("buildIndex");
            if (model.enableTracing) {
                b.string("Arrays.copyOf(basicBlockBoundary, bci)");
            }
            b.end(2);

            if (model.enableYield) {
                b.startFor().string("ContinuationLocation location : continuationLocations").end().startBlock();
                b.statement("ContinuationLocationImpl locationImpl = (ContinuationLocationImpl) location");
                b.statement("locationImpl.rootNode = new ContinuationRootNodeImpl(language, result.getFrameDescriptor(), result, (locationImpl.sp << 16) | locationImpl.bci)");
                b.end();
            }

            b.startAssert().string("builtNodes.size() == buildIndex").end();
            b.statement("builtNodes.add(result)");

            b.end(); // }

            b.statement("buildIndex++");

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

        private void emitCastOperationData(CodeTreeBuilder b, String dataClassName, String operationSp) {
            b.startIf();
            b.string("!(operationStack[" + operationSp + "].data instanceof ");
            b.string(dataClassName);
            b.string(" operationData)");
            b.end().startBlock();
            emitThrowAssertionError(b, "\"Data class " + dataClassName + " expected, but was \" + operationStack[operationSp].data");
            b.end();
        }

        private void buildEmitOperationInstruction(CodeTreeBuilder b, OperationModel operation) {
            String[] args = switch (operation.kind) {
                case LOAD_LOCAL -> new String[]{"((BytecodeLocalImpl) " + operation.getOperationArgumentName(0) + ").index"};
                case STORE_LOCAL, STORE_LOCAL_MATERIALIZED -> {
                    if (model.usesBoxingElimination()) {
                        yield new String[]{"((StoreLocalData) operationStack[operationSp].data).local.index", "((StoreLocalData) operationStack[operationSp].data).childBci"};
                    } else {
                        yield new String[]{"((BytecodeLocalImpl) operationStack[operationSp].data).index"};
                    }
                }
                case LOAD_LOCAL_MATERIALIZED -> new String[]{"((BytecodeLocalImpl) operationStack[operationSp].data).index"};
                case RETURN -> new String[]{};
                case LOAD_ARGUMENT -> new String[]{operation.getOperationArgumentName(0)};
                case LOAD_CONSTANT -> new String[]{"constantPool.addConstant(" + operation.getOperationArgumentName(0) + ")"};
                case BRANCH -> {
                    b.startAssign("BytecodeLabelImpl labelImpl").string("(BytecodeLabelImpl) " + operation.getOperationArgumentName(0)).end();

                    b.statement("boolean isFound = false");
                    b.startFor().string("int i = 0; i < operationSp; i++").end().startBlock();
                    b.startIf().string("operationStack[i].sequenceNumber == labelImpl.declaringOp").end().startBlock();
                    b.statement("isFound = true");
                    b.statement("break");
                    b.end();
                    b.end();

                    b.startIf().string("!isFound").end().startBlock();
                    emitThrowIllegalStateException(b, "\"Branch must be targeting a label that is declared in an enclosing operation. Jumps into other operations are not permitted.\"");
                    b.end();

                    b.statement("doEmitLeaves(labelImpl.declaringOp)");

                    b.startIf().string("labelImpl.isDefined()").end().startBlock();
                    emitThrowIllegalStateException(b, "\"Backward branches are unsupported. Use a While operation to model backward control flow.\"");
                    b.end();
                    // Mark the branch target as uninitialized. Add this location to a work list to
                    // be processed once the label is defined.
                    b.startStatement().startCall("registerUnresolvedLabel");
                    b.string("labelImpl");
                    b.string("bci + 1");
                    b.string("currentStackHeight");
                    b.end(2);
                    b.newLine();

                    // Branches inside finally handlers can only be relative to the handler,
                    // otherwise a finally handler emitted before a "return" could branch out of the
                    // handler and circumvent the return.
                    b.startIf().string("inFinallyTryHandler(finallyTryContext)").end().startBlock();

                    b.startIf().string("labelImpl.finallyTryOp != finallyTryContext.finallyTrySequenceNumber").end().startBlock();
                    emitThrowIllegalStateException(b, "\"Branches inside finally handlers can only target labels defined in the same handler.\"");
                    b.end();

                    b.lineComment("We need to track branch targets inside finally handlers so that they can be adjusted each time the handler is emitted.");
                    b.statement("finallyTryContext.finallyRelativeBranches.add(bci + 1)");

                    b.end();
                    yield new String[]{UNINIT};
                }
                case YIELD -> {
                    b.statement("ContinuationLocation continuation = new ContinuationLocationImpl(numYields++, bci + 2, currentStackHeight)");
                    b.statement("continuationLocations.add(continuation)");
                    yield new String[]{"constantPool.addConstant(continuation)"};
                }
                case CUSTOM_SIMPLE -> buildCustomInitializer(b, operation, operation.instruction);
                case CUSTOM_SHORT_CIRCUIT -> throw new AssertionError("Tried to emit a short circuit instruction directly. These operations should only be emitted implicitly.");
                default -> throw new AssertionError("Reached an operation " + operation.name + " that cannot be initialized. This is a bug in the Bytecode DSL processor.");
            };
            buildEmitInstruction(b, operation.instruction, args);
        }

        private CodeExecutableElement createEmitOperationBegin() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "emitOperationBegin");

            CodeTreeBuilder b = ex.createBuilder();

            b.startIf().string("operationStack == null").end().startBlock(); // {
            b.startThrow().startNew(context.getType(IllegalStateException.class));
            b.string("\"Unexpected operation emit - no root operation present. Did you forget a beginRoot()?\"").end();
            b.end(2);
            b.end(); // }

            return ex;
        }

        private CodeExecutableElement createEmit(OperationModel operation) {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), context.getType(void.class), "emit" + operation.name);
            ex.setVarArgs(operation.operationArgumentVarArgs);

            for (OperationArgument arg : operation.operationArguments) {
                ex.addParameter(arg.toVariableElement());
            }

            addBeginOrEmitOperationDoc(operation, ex);

            CodeTreeBuilder b = ex.createBuilder();

            if (model.enableSerialization) {
                b.startIf().string("serialization != null").end().startBlock();
                createSerializeBegin(operation, b);
                b.statement("return");
                b.end();
            }

            b.startStatement().startCall("beforeChild").end(2);
            b.startStatement().startCall("emitOperationBegin").end(2);

            if (operation.kind == OperationKind.LABEL) {
                b.startAssign("BytecodeLabelImpl labelImpl").string("(BytecodeLabelImpl) " + operation.getOperationArgumentName(0)).end();

                b.startIf().string("labelImpl.isDefined()").end().startBlock();
                emitThrowIllegalStateException(b, "\"BytecodeLabel already emitted. Each label must be emitted exactly once.\"");
                b.end();

                b.startIf().string("labelImpl.declaringOp != operationStack[operationSp - 1].sequenceNumber").end().startBlock();
                emitThrowIllegalStateException(b, "\"BytecodeLabel must be emitted inside the same operation it was created in.\"");
                b.end();

                b.statement("labelImpl.bci = bci");
                b.startStatement().startCall("resolveUnresolvedLabel");
                b.string("labelImpl");
                b.string("currentStackHeight");
                b.end(2);
            } else {
                assert operation.instruction != null;
                buildEmitOperationInstruction(b, operation);
            }

            b.startStatement().startCall("afterChild");
            b.string("" + !operation.isVoid);
            b.string(operation.instruction != null ? "bci - " + operation.instruction.getInstructionLength() : "-1");
            b.end(2);

            return ex;
        }

        private String[] buildCustomInitializer(CodeTreeBuilder b, OperationModel operation, InstructionModel instruction) {
            assert operation.kind != OperationKind.CUSTOM_SHORT_CIRCUIT;

            if (instruction.signature.isVariadic) {
                // Before emitting a variadic instruction, we need to emit instructions to merge all
                // of the operands on the stack into one array.
                b.statement("doEmitVariadic(operationStack[operationSp].childCount - " + (instruction.signature.valueCount - 1) + ")");
            }

            boolean inEmit = operation.numChildren == 0;

            if (!inEmit) {
                // make "operationData" available for endX methods.
                emitCastOperationData(b, "CustomOperationData", "operationSp");
            }

            List<InstructionImmediate> immediates = instruction.getImmediates();
            String[] args = new String[immediates.size()];

            int childNodeIndex = 0;
            int localSetterIndex = 0;
            int localSetterRangeIndex = 0;
            for (int i = 0; i < immediates.size(); i++) {
                InstructionImmediate immediate = immediates.get(i);
                args[i] = switch (immediate.kind()) {
                    case BYTECODE_INDEX -> {
                        String child = "child" + childNodeIndex;
                        b.startAssign("int " + child);
                        b.string("operationData.childBcis[" + childNodeIndex + "]");
                        b.end();
                        childNodeIndex++;

                        yield child;
                    }
                    case LOCAL_SETTER -> {
                        String arg = "localSetter" + localSetterIndex;
                        b.startAssign("int " + arg);
                        if (inEmit) {
                            b.string("((BytecodeLocalImpl) " + operation.getOperationArgumentName(localSetterIndex) + ").index");
                        } else {
                            b.string("((BytecodeLocalImpl) operationData.locals[" + localSetterIndex + "]).index");
                        }
                        b.end();
                        b.startStatement();
                        b.startStaticCall(types.LocalSetter, "create");
                        b.string(arg);
                        b.end(2);
                        localSetterIndex++;

                        yield arg;
                    }
                    case LOCAL_SETTER_RANGE_START -> {
                        String locals = "range" + localSetterRangeIndex + "Locals";
                        String indices = "range" + localSetterRangeIndex + "Indices";
                        String range = "range" + localSetterRangeIndex;

                        // Get array of locals (from named argument/operation Stack).
                        b.startAssign("BytecodeLocal[] " + locals);
                        if (inEmit) {
                            b.string(operation.getOperationArgumentName(localSetterIndex + localSetterRangeIndex));
                        } else {
                            b.string("(BytecodeLocal[]) operationData.locals[" + (localSetterIndex + localSetterRangeIndex) + "]");
                        }
                        b.end();

                        // Convert to an array of local indices.
                        b.startAssign("int[] " + indices);
                        b.string("new int[" + locals + ".length]");
                        b.end();
                        b.startFor().string("int i = 0; i < " + indices + ".length; i++").end().startBlock();
                        b.startAssign(indices + "[i]").string("((BytecodeLocalImpl) " + locals + "[i]).index").end();
                        b.end();

                        // Create range from indices (create method validates that locals are
                        // contiguous).
                        b.startAssign("LocalSetterRange " + range);
                        b.startStaticCall(types.LocalSetterRange, "create");
                        b.string(indices);
                        b.end(2);

                        String start = "localSetterRangeStart" + localSetterRangeIndex;
                        String length = "localSetterRangeLength" + localSetterRangeIndex;
                        b.declaration(context.getType(int.class), start, range + ".start");
                        b.declaration(context.getType(int.class), length, range + ".length");

                        yield start;
                    }
                    case LOCAL_SETTER_RANGE_LENGTH -> {
                        // NB: this is a bit of a hack. We rely on the previous block to create the
                        // LocalSetterRange and set the length variable, and just yield it here.
                        yield "localSetterRangeLength" + (localSetterRangeIndex++);
                    }
                    case NODE_PROFILE -> "allocateNode()";
                    case INTEGER, CONSTANT, BRANCH_PROFILE -> throw new AssertionError(
                                    "Operation " + operation.name + " takes an immediate " + immediate.name() + " with unexpected kind " + immediate.kind() +
                                                    ". This is a bug in the Bytecode DSL processor.");
                };
            }

            return args;
        }

        private CodeExecutableElement createBeforeChild() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "beforeChild");
            CodeTreeBuilder b = ex.createBuilder();

            createCheckRoot(b);

            b.statement("int childIndex = operationStack[operationSp - 1].childCount");
            b.startSwitch().string("operationStack[operationSp - 1].operation").end().startBlock();

            Map<Integer, List<OperationModel>> groupedOperations = model.getOperations().stream().filter(OperationModel::hasChildren).collect(Collectors.groupingBy(op -> {
                if (op.isTransparent && (op.isVariadic || op.numChildren > 1)) {
                    return 1; // needs to pop
                } else if (op.kind == OperationKind.CUSTOM_SHORT_CIRCUIT) {
                    return 2; // short circuit
                } else {
                    return 3; // do nothing
                }
            }));
            List<OperationModel> popOperations = groupedOperations.get(1);
            List<OperationModel> shortCircuitOperations = groupedOperations.get(2);
            List<OperationModel> doNothingOperationModels = groupedOperations.get(3);

            if (popOperations != null) {
                for (OperationModel op : popOperations) {
                    b.startCase().tree(createOperationConstant(op)).end();
                }
                b.startBlock();
                emitCastOperationData(b, "TransparentOperationData", "operationSp - 1");
                b.startIf().string("operationData.producedValue").end().startBlock();
                buildEmitInstruction(b, model.popInstruction, emitPopArguments("operationData.childBci"));
                b.end();
                b.statement("break");
                b.end();
            }

            if (shortCircuitOperations != null) {
                for (OperationModel op : shortCircuitOperations) {
                    b.startCase().tree(createOperationConstant(op)).end().startBlock();

                    b.startIf().string("childIndex != 0").end().startBlock();
                    if (!op.instruction.shortCircuitModel.returnConvertedBoolean()) {
                        // DUP so the boolean converter doesn't clobber the original value.
                        buildEmitInstruction(b, model.dupInstruction);
                    }
                    emitCastOperationData(b, "CustomShortCircuitOperationData", "operationSp - 1");

                    b.declaration(type(int.class), "converterBci", "bci");

                    buildEmitBooleanConverterInstruction(b, op.instruction);
                    b.statement("operationData.branchFixupBcis.add(bci + " + op.instruction.getImmediate("branch_target").offset() + ")");
                    buildEmitInstruction(b, op.instruction, emitShortCircuitArguments(op.instruction));
                    b.end();

                    b.statement("break");
                    b.end();
                }
            }

            if (doNothingOperationModels != null) {
                for (OperationModel op : doNothingOperationModels) {
                    b.startCase().tree(createOperationConstant(op)).end();
                }
                b.startBlock();
                b.statement("break");
                b.end();
            }

            b.caseDefault();
            b.startBlock();
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

        private void createCheckRoot(CodeTreeBuilder b) {
            b.startIf().string("operationStack == null").end().startBlock(); // {
            b.startThrow().startNew(context.getType(IllegalStateException.class));
            b.string("\"Unexpected operation begin - no root operation present. Did you forget a beginRoot()?\"").end();
            b.end(2);
            b.end(); // }
        }

        private CodeExecutableElement createAfterChild() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "afterChild");
            ex.addParameter(new CodeVariableElement(context.getType(boolean.class), "producedValue"));
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "childBci"));
            CodeTreeBuilder b = ex.createBuilder();

            b.statement("int childIndex = operationStack[operationSp - 1].childCount");

            b.startSwitch().string("operationStack[operationSp - 1].operation").end().startBlock();

            Map<Boolean, List<OperationModel>> operationsByTransparency = model.getOperations().stream() //
                            .filter(OperationModel::hasChildren).collect(Collectors.partitioningBy(OperationModel::isTransparent));

            // First, do transparent operations (grouped).
            assert !operationsByTransparency.get(true).isEmpty();
            for (OperationModel op : operationsByTransparency.get(true)) {
                b.startCase().tree(createOperationConstant(op)).end();
            }
            b.startBlock();
            emitCastOperationData(b, "TransparentOperationData", "operationSp - 1");
            b.statement("operationData.producedValue = producedValue");
            b.statement("operationData.childBci = childBci");
            b.statement("break");
            b.end();

            // Then, do non-transparent operations (separately).
            for (OperationModel op : operationsByTransparency.get(false)) {
                b.startCase().tree(createOperationConstant(op)).end().startBlock();
                /**
                 * Ensure the stack balances. If a value was expected, assert that the child
                 * produced a value. If a value was not expected but the child produced one, pop it.
                 */
                if (op.childrenMustBeValues != null) {
                    List<Integer> valueChildren = new ArrayList<>();
                    List<Integer> nonValueChildren = new ArrayList<>();

                    for (int i = 0; i < op.childrenMustBeValues.length; i++) {
                        if (op.childrenMustBeValues[i]) {
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
                            String operator = (op.isVariadic && valueChildren.get(i) == op.childrenMustBeValues.length - 1) ? ">=" : "==";
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
                            String operator = (op.isVariadic && nonValueChildren.get(i) == op.childrenMustBeValues.length - 1) ? ">=" : "==";
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
                        /**
                         * To avoid reading past the end of the bytecode, we insert a TRAP at the
                         * end. If the root ends with an instruction that definitely cannot fall
                         * through, we can omit it.
                         *
                         * NB: This is a simple heuristic, but it is conservative: it should not
                         * incorrectly omit a TRAP. Certain problematic cases (e.g., IfThenElse
                         * operations, a label after the last instruction) are avoided because the
                         * childBci will be -1.
                         */
                        emitCastOperationData(b, "RootData", "operationSp - 1");
                        b.startIf().string("childBci != -1").end().startBlock();
                        b.startSwitch().string(readBc("childBci")).end().startBlock(); // switch
                        List<InstructionModel> nonFallthroughInstructions = List.of(model.returnInstruction, model.branchBackwardInstruction, model.throwInstruction, model.trapInstruction);
                        for (InstructionModel instr : nonFallthroughInstructions) {
                            b.startCase().tree(createInstructionConstant(instr)).end();
                        }
                        b.startBlock();
                        b.statement("operationData.mayFallThrough = false");
                        b.statement("break");
                        b.end();
                        b.caseDefault().startBlock();
                        b.statement("operationData.mayFallThrough = true");
                        b.end();
                        b.end(); // switch
                        b.end().startElseBlock();
                        b.statement("operationData.mayFallThrough = true");
                        b.end();
                        break;
                    case IF_THEN:
                        emitCastOperationData(b, "IfThenData", "operationSp - 1");
                        b.startIf().string("childIndex == 0").end().startBlock();
                        b.statement("operationData.falseBranchFixupBci = bci + " + model.branchFalseInstruction.getImmediates(ImmediateKind.BYTECODE_INDEX).get(0).offset());
                        emitFinallyRelativeBranchCheck(b, 1);
                        buildEmitInstruction(b, model.branchFalseInstruction, emitBranchArguments(model.branchFalseInstruction));
                        b.end().startElseBlock();
                        b.statement("int toUpdate = operationData.falseBranchFixupBci");
                        b.statement(writeBc("toUpdate", "(short) bci"));
                        b.end();
                        if (model.enableTracing) {
                            b.statement("basicBlockBoundary[bci] = true");
                        }
                        break;
                    case IF_THEN_ELSE:
                        emitCastOperationData(b, "IfThenElseData", "operationSp - 1");
                        b.startIf().string("childIndex == 0").end().startBlock();
                        b.statement("operationData.falseBranchFixupBci = bci + 1");
                        emitFinallyRelativeBranchCheck(b, 1);

                        buildEmitInstruction(b, model.branchFalseInstruction, emitBranchArguments(model.branchFalseInstruction));
                        b.end().startElseIf().string("childIndex == 1").end().startBlock();
                        b.statement("operationData.endBranchFixupBci = bci + 1");
                        emitFinallyRelativeBranchCheck(b, 1);
                        buildEmitInstruction(b, model.branchInstruction, new String[]{UNINIT});
                        b.statement("int toUpdate = operationData.falseBranchFixupBci");
                        b.statement(writeBc("toUpdate", "(short) bci"));
                        b.end().startElseBlock();
                        b.statement("int toUpdate = operationData.endBranchFixupBci");
                        b.statement(writeBc("toUpdate", "(short) bci"));
                        b.end();
                        if (model.enableTracing) {
                            b.statement("basicBlockBoundary[bci] = true");
                        }
                        break;
                    case CONDITIONAL:
                        emitCastOperationData(b, "ConditionalData", "operationSp - 1");
                        b.startIf().string("childIndex == 0").end().startBlock();
                        if (model.usesBoxingElimination()) {
                            buildEmitInstruction(b, model.dupInstruction);
                        }
                        b.statement("operationData.falseBranchFixupBci = bci + 1");
                        emitFinallyRelativeBranchCheck(b, 1);

                        buildEmitInstruction(b, model.branchFalseInstruction, emitBranchArguments(model.branchFalseInstruction));
                        b.end().startElseIf().string("childIndex == 1").end().startBlock();
                        if (model.usesBoxingElimination()) {
                            b.statement("operationData.child0Bci = childBci");
                        }
                        b.statement("operationData.endBranchFixupBci = bci + 1");

                        emitFinallyRelativeBranchCheck(b, 1);
                        buildEmitInstruction(b, model.branchInstruction, new String[]{UNINIT});
                        // we have to adjust the stack for the third child
                        b.statement("currentStackHeight -= 1");
                        b.statement("int toUpdate = operationData.falseBranchFixupBci");
                        b.statement(writeBc("toUpdate", "(short) bci"));
                        b.end().startElseBlock();
                        if (model.usesBoxingElimination()) {
                            b.statement("operationData.child1Bci = childBci");
                        }
                        b.statement("int toUpdate = operationData.endBranchFixupBci");
                        b.statement(writeBc("toUpdate", "(short) bci"));
                        b.end();
                        if (model.enableTracing) {
                            b.statement("basicBlockBoundary[bci] = true");
                        }
                        break;
                    case WHILE:
                        emitCastOperationData(b, "WhileData", "operationSp - 1");
                        b.startIf().string("childIndex == 0").end().startBlock();
                        b.statement("operationData.endBranchFixupBci = bci + 1");
                        emitFinallyRelativeBranchCheck(b, 1);

                        buildEmitInstruction(b, model.branchFalseInstruction, emitBranchArguments(model.branchFalseInstruction));
                        b.end().startElseBlock();
                        emitFinallyRelativeBranchCheck(b, 1);
                        buildEmitInstruction(b, model.branchBackwardInstruction, new String[]{"(short) operationData.whileStartBci"});
                        b.statement("int toUpdate = operationData.endBranchFixupBci");
                        b.statement(writeBc("toUpdate", "(short) bci"));
                        b.end();
                        if (model.enableTracing) {
                            b.statement("basicBlockBoundary[bci] = true");
                        }
                        break;
                    case TRY_CATCH:
                        emitCastOperationData(b, "TryCatchData", "operationSp - 1");
                        b.startIf().string("childIndex == 0").end().startBlock();
                        b.statement("operationData.tryEndBci = bci");
                        b.statement("operationData.endBranchFixupBci = bci + 1");
                        emitFinallyRelativeBranchCheck(b, 1);
                        buildEmitInstruction(b, model.branchInstruction, new String[]{UNINIT});
                        b.statement("operationData.catchStartBci = bci");
                        b.end();
                        b.startElseIf().string("childIndex == 1").end().startBlock();
                        b.statement("int toUpdate = operationData.endBranchFixupBci");
                        b.statement(writeBc("toUpdate", "(short) bci"));
                        b.statement("doCreateExceptionHandler(operationData.tryStartBci, operationData.tryEndBci, operationData.catchStartBci, operationData.startStackHeight, operationData.exceptionLocalIndex)");
                        b.end();
                        if (model.enableTracing) {
                            b.statement("basicBlockBoundary[bci] = true");
                        }
                        break;
                    case FINALLY_TRY:
                    case FINALLY_TRY_NO_EXCEPT:
                        b.startIf().string("childIndex == 0").end().startBlock();

                        /**
                         * Each time we emit the handler, we need to keep track of any branches that
                         * haven't yet been resolved. We create reverse mappings for efficient
                         * lookup of the unknown label and the stack height at the branch
                         * instruction.
                         */
                        b.declaration(
                                        generic(HashMap.class, context.getDeclaredType(Integer.class), types.BytecodeLabel),
                                        "unresolvedBranchLabels",
                                        CodeTreeBuilder.createBuilder().startStaticCall(bytecodeBuilderType, "createBranchLabelMapping").string("unresolvedLabels").end());
                        b.declaration(
                                        generic(HashMap.class, context.getDeclaredType(Integer.class), context.getDeclaredType(Integer.class)),
                                        "unresolvedBranchStackHeights",
                                        CodeTreeBuilder.createBuilder().startStaticCall(bytecodeBuilderType, "createBranchStackHeightMapping").string("unresolvedLabels").end());

                        b.startStatement().startCall("finallyTryContext", "setHandler");
                        b.string("Arrays.copyOf(bc, bci)");
                        b.string("maxStackHeight");
                        b.string("parseSources ? Arrays.copyOf(sourceInfo, sourceInfoIndex) : null");
                        b.string("Arrays.copyOf(exHandlers, exHandlerCount)");
                        b.string("unresolvedBranchLabels");
                        b.string("unresolvedBranchStackHeights");
                        if (model.enableTracing) {
                            b.string("basicBlockBoundary");
                        }
                        b.end(2);

                        for (CodeVariableElement field : builderContextSensitiveState) {
                            b.startAssign(field.getName()).field("finallyTryContext", field).end();
                        }
                        b.statement("finallyTryContext = finallyTryContext.parentContext");

                        b.end();
                        break;
                    case STORE_LOCAL:
                    case STORE_LOCAL_MATERIALIZED:
                        if (model.usesBoxingElimination()) {
                            emitCastOperationData(b, "StoreLocalData", "operationSp - 1");
                            b.statement("operationData.childBci = childBci");
                        } else {
                            // no operand to encode
                        }
                        break;
                    case CUSTOM_SIMPLE:
                        int immediateIndex = 0;
                        boolean elseIf = false;
                        boolean operationDataEmitted = false;
                        for (int valueIndex = 0; valueIndex < op.instruction.signature.valueCount; valueIndex++) {
                            if (op.instruction.needsBoxingElimination(model, valueIndex)) {
                                if (!operationDataEmitted) {
                                    emitCastOperationData(b, "CustomOperationData", "operationSp - 1");
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
                        ShortCircuitInstructionModel shortCircuitInstruction = op.instruction.shortCircuitModel;
                        if (model.isBoxingEliminated(type(boolean.class)) && shortCircuitInstruction.booleanConverterInstruction().needsBoxingElimination(model, 0)) {
                            emitCastOperationData(b, "CustomShortCircuitOperationData", "operationSp - 1");
                            b.statement("operationData.childBci = childBci");
                        }
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

        private void buildCalculateNewLengthOfArray(CodeTreeBuilder b, String start, String target) {
            b.statement("int resultLength = " + start);

            b.startWhile().string(target, " > resultLength").end().startBlock();
            b.statement("resultLength *= 2");
            b.end();
        }

        private CodeExecutableElement createDoEmitFinallyHandler() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "doEmitFinallyHandler");
            ex.addParameter(new CodeVariableElement(finallyTryContext.asType(), "context"));
            CodeTreeBuilder b = ex.createBuilder();

            b.statement("int offsetBci = bci");
            b.statement("short[] handlerBc = context.handlerBc");

            // resize all arrays
            b.startIf().string("bci + handlerBc.length > bc.length").end().startBlock();
            buildCalculateNewLengthOfArray(b, "bc.length", "bci + handlerBc.length");

            b.statement("bc = Arrays.copyOf(bc, resultLength)");
            if (model.enableTracing) {
                b.statement("basicBlockBoundary = Arrays.copyOf(basicBlockBoundary, resultLength + 1)");
            }
            b.end();

            b.startIf().string("parseSources && sourceInfoIndex + context.handlerSourceInfo.length > sourceInfo.length").end().startBlock();
            buildCalculateNewLengthOfArray(b, "sourceInfo.length", "sourceInfoIndex + context.handlerSourceInfo.length ");
            b.statement("sourceInfo = Arrays.copyOf(sourceInfo, resultLength)");
            b.end();

            b.startIf().string("exHandlerCount + context.handlerExHandlers.length > exHandlers.length").end().startBlock();
            buildCalculateNewLengthOfArray(b, "exHandlers.length", "exHandlerCount + context.handlerExHandlers.length");
            b.statement("exHandlers = Arrays.copyOf(exHandlers, resultLength)");
            b.end();

            b.statement("System.arraycopy(context.handlerBc, 0, bc, bci, context.handlerBc.length)");
            if (model.enableTracing) {
                b.statement("System.arraycopy(context.handlerBasicBlockBoundary, 0, basicBlockBoundary, bci, context.handlerBc.length + 1)");
            }

            b.startFor().string("int handlerBci = 0; handlerBci < handlerBc.length;").end().startBlock();
            b.startSwitch().string(readHandlerBc("handlerBci")).end().startBlock();

            // Fix up instructions.
            Map<Boolean, List<InstructionModel>> builtinsGroupedByNeedsRelocation = model.getInstructions().stream().//
                            filter(i -> !i.isQuickening()).//
                            filter(i -> !i.isCustomInstruction()).//
                            collect(Collectors.partitioningBy(instr -> needsRelocation(instr)));

            Map<Integer, List<InstructionModel>> nonRelocatingBuiltinsGroupedByLength = builtinsGroupedByNeedsRelocation.get(false).stream().collect(
                            Collectors.groupingBy(InstructionModel::getInstructionLength));

            Map<List<InstructionImmediate>, List<InstructionModel>> customInstructionsGroupedByEncoding = model.getInstructions().stream().//
                            filter(i -> !i.isQuickening()).//
                            filter(i -> i.isCustomInstruction()).//
                            collect(Collectors.groupingBy(instr -> instr.getImmediates()));

            // Non-relocatable builtins (one case per instruction length)
            for (Map.Entry<Integer, List<InstructionModel>> entry : nonRelocatingBuiltinsGroupedByLength.entrySet()) {
                for (InstructionModel instr : entry.getValue()) {
                    b.startCase().tree(createInstructionConstant(instr)).end();
                    if (needsRelocation(instr)) {
                        throw new AssertionError("Inconsistent grouping");
                    }
                }
                b.startCaseBlock();
                b.statement("handlerBci += " + entry.getKey());
                b.statement("break");
                b.end();
            }

            // Relocatable builtins
            for (InstructionModel instr : builtinsGroupedByNeedsRelocation.get(true)) {
                if (instr.isQuickening()) {
                    throw new AssertionError("unexpected quickening");
                }
                b.startCase().tree(createInstructionConstant(instr)).end();
                b.startCaseBlock();
                relocateImmediates(b, instr);
                b.statement("handlerBci += " + instr.getInstructionLength());
                b.statement("break");
                b.end();
            }

            // group by instruction immediates
            for (List<InstructionModel> instrs : customInstructionsGroupedByEncoding.values()) {
                InstructionModel instr = instrs.get(0);
                for (InstructionModel otherInstruction : instrs) {
                    b.startCase().tree(createInstructionConstant(otherInstruction)).end();
                    if (instr.getInstructionLength() != otherInstruction.getInstructionLength()) {
                        throw new AssertionError("Unexpected instruction length mismatch.");
                    }
                }
                b.startCaseBlock();
                relocateImmediates(b, instr);
                b.statement("handlerBci += " + instr.getInstructionLength());
                b.statement("break");
                b.end();
            }

            b.caseDefault();
            b.startCaseBlock();
            b.tree(GeneratorUtils.createShouldNotReachHere("Unexpected instructions."));
            b.end();

            b.end(); // switch
            b.end(); // for

            b.statement("bci += handlerBc.length");

            b.startIf().string("currentStackHeight + context.handlermaxStackHeight > maxStackHeight").end().startBlock();
            b.statement("maxStackHeight = currentStackHeight + context.handlermaxStackHeight");
            b.end();

            b.startIf().string("parseSources").end().startBlock();
            b.startFor().string("int idx = 0; idx < context.handlerSourceInfo.length; idx += 3").end().startBlock();
            b.statement("sourceInfo[sourceInfoIndex + idx] = context.handlerSourceInfo[idx] + offsetBci");
            b.statement("sourceInfo[sourceInfoIndex + idx + 1] = context.handlerSourceInfo[idx + 1]");
            b.statement("sourceInfo[sourceInfoIndex + idx + 2] = context.handlerSourceInfo[idx + 2]");
            b.end();

            b.statement("sourceInfoIndex += context.handlerSourceInfo.length");
            b.end();

            b.startFor().string("int idx = 0; idx < context.handlerExHandlers.length; idx += 5").end().startBlock();
            b.statement("exHandlers[exHandlerCount + idx] = context.handlerExHandlers[idx] + offsetBci");
            b.statement("exHandlers[exHandlerCount + idx + 1] = context.handlerExHandlers[idx + 1] + offsetBci");
            b.statement("exHandlers[exHandlerCount + idx + 2] = context.handlerExHandlers[idx + 2] + offsetBci");
            b.statement("exHandlers[exHandlerCount + idx + 3] = context.handlerExHandlers[idx + 3] + currentStackHeight");
            b.statement("exHandlers[exHandlerCount + idx + 4] = context.handlerExHandlers[idx + 4]");
            b.end();

            b.statement("exHandlerCount += context.handlerExHandlers.length");

            return ex;
        }

        private boolean needsRelocation(InstructionModel instr) {
            if (instr.kind == InstructionKind.YIELD) { // has no immediates but still needs
                return true;
            }
            return instr.getImmediates().stream().filter((i) -> needsRelocation(i)).findAny().isPresent();
        }

        private static boolean needsRelocation(InstructionImmediate i) {
            return switch (i.kind()) {
                case CONSTANT, INTEGER, LOCAL_SETTER, LOCAL_SETTER_RANGE_START, LOCAL_SETTER_RANGE_LENGTH -> false;
                case BYTECODE_INDEX, NODE_PROFILE, BRANCH_PROFILE -> true;
                default -> throw new AssertionError("Unexpected kind");
            };
        }

        private void relocateImmediates(CodeTreeBuilder b, InstructionModel instr) {
            if (!needsRelocation(instr)) {
                throw new AssertionError("Inconsistent grouping");
            }

            // instruction specific logic
            switch (instr.kind) {
                case YIELD:
                    b.statement("int locationBci = handlerBci + 1");
                    b.statement("ContinuationLocationImpl cl = (ContinuationLocationImpl) constantPool.getConstant(" + readHandlerBc("locationBci") + ")");
                    // The continuation should resume after this yield instruction
                    b.statement("assert cl.bci == locationBci + 1");
                    b.statement("ContinuationLocationImpl newContinuation = new ContinuationLocationImpl(numYields++, offsetBci + cl.bci, currentStackHeight + cl.sp)");
                    b.statement(writeBc("offsetBci + locationBci", "(short) constantPool.addConstant(newContinuation)"));
                    b.statement("continuationLocations.add(newContinuation)");
                    break;
            }

            List<InstructionImmediate> immediates = instr.getImmediates();
            for (int i = 0; i < immediates.size(); i++) {
                InstructionImmediate immediate = immediates.get(i);
                switch (immediate.kind()) {
                    case BYTECODE_INDEX:
                        if (immediate.name().startsWith("child")) {
                            // Custom operations don't have non-local branches/children, so
                            // this immediate is *always* relative.
                            b.statement(writeBc("offsetBci + handlerBci + " + immediate.offset(),
                                            "(short) (" + readBc("offsetBci + handlerBci + " + immediate.offset()) + " + offsetBci) /* adjust " + immediate.name() + " */"));
                        } else if (immediate.name().equals("branch_target")) {
                            relocateBranchTarget(b, instr, immediate);
                        } else {
                            throw new AssertionError("Unexpected bytecode index immediate");
                        }
                        break;
                    case NODE_PROFILE:
                        // Allocate a separate Node for each handler.
                        b.statement(writeBc("offsetBci + handlerBci + " + immediate.offset(), "(short) allocateNode()"));
                        break;
                    case BRANCH_PROFILE:
                        b.statement(writeBc("offsetBci + handlerBci + " + immediate.offset(), "(short) allocateBranchProfile()"));
                        break;
                    case CONSTANT:
                    case INTEGER:
                    case LOCAL_SETTER:
                    case LOCAL_SETTER_RANGE_LENGTH:
                    case LOCAL_SETTER_RANGE_START:
                        // nothing to relocate
                        break;
                    default:
                        throw new AssertionError("Unexpected immediate");
                }
            }
        }

        private void relocateBranchTarget(CodeTreeBuilder b, InstructionModel instr, InstructionImmediate immediate) {
            switch (instr.kind) {
                case BRANCH:
                case BRANCH_BACKWARD:
                case BRANCH_FALSE:
                    b.startBlock();
                    b.statement("int branchIdx = handlerBci + 1"); // BCI of branch
                    b.statement("short branchTarget = " + readHandlerBc("branchIdx"));
                    if (instr.kind == InstructionKind.BRANCH_BACKWARD) {
                        // Backward branches are only used internally by while
                        // loops. They
                        // should be resolved when the while loop ends.
                        b.startAssert().string("branchTarget != " + UNINIT).end();
                    } else {
                        // Mark branch target as unresolved, if necessary.
                        b.startIf().string("branchTarget == " + UNINIT).end().startBlock();
                        b.lineComment("This branch is to a not-yet-emitted label defined by an outer operation.");
                        b.statement("BytecodeLabelImpl lbl = (BytecodeLabelImpl) context.handlerUnresolvedBranchLabels.get(branchIdx)");
                        b.statement("int sp = context.handlerUnresolvedBranchStackHeights.get(branchIdx)");
                        b.statement("assert !lbl.isDefined()");
                        b.startStatement().startCall("registerUnresolvedLabel");
                        b.string("lbl");
                        b.string("offsetBci + branchIdx");
                        b.string("currentStackHeight + sp");
                        b.end(3);
                    }

                    b.newLine();

                    // Adjust relative branch targets.
                    b.startIf().string("context.finallyRelativeBranches.contains(branchIdx)").end().startBlock();
                    b.statement(writeBc("offsetBci + branchIdx", "(short) (offsetBci + branchTarget)") + " /* relocated */");
                    b.startIf().string("inFinallyTryHandler(context.parentContext)").end().startBlock();
                    b.lineComment("If we're currently nested inside some other finally handler, the branch will also need to be relocated in that handler.");
                    b.statement("context.parentContext.finallyRelativeBranches.add(offsetBci + branchIdx)");
                    b.end();
                    b.end().startElseBlock();
                    b.statement(writeBc("offsetBci + branchIdx", "branchTarget"));
                    b.end();
                    b.end();
                    break;
                case CUSTOM_SHORT_CIRCUIT:
                    b.statement(writeBc("offsetBci + handlerBci + " + immediate.offset(),
                                    "(short) (" + readBc("offsetBci + handlerBci + " + immediate.offset()) + " + offsetBci) /* adjust " + immediate.name() + " */"));
                    break;
                default:
                    throw new AssertionError("Unexpected instruction with branch target: " + instr.name);
            }
        }

        private CodeExecutableElement ensureDoEmitInstructionCreated(int argumentLength) {
            return doEmitInstructionMethods.computeIfAbsent(argumentLength, (length) -> createDoEmitInstruction(length));
        }

        private CodeExecutableElement createDoEmitInstruction(int argumentLength) {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "doEmitInstruction");
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "instruction"));
            for (int i = 0; i < argumentLength; i++) {
                ex.addParameter(new CodeVariableElement(type(int.class), "data" + i));
            }
            CodeTreeBuilder b = ex.createBuilder();
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

            int variadicCount = model.popVariadicInstruction.length - 1;

            b.startIf().string("count <= ").string(variadicCount).end().startBlock();
            b.startStatement().string("doEmitInstruction(").tree(createInstructionConstant(model.popVariadicInstruction[0])).string(" + count)").end();
            b.end().startElseBlock();

            b.startIf().string("currentStackHeight + 1 > maxStackHeight").end().startBlock();
            b.statement("maxStackHeight = currentStackHeight + 1");
            b.end();
            b.statement("int elementCount = count + 1");
            b.startStatement().startCall("doEmitInstruction").tree(createInstructionConstant(model.storeNullInstruction)).end(2);

            b.startWhile().string("elementCount > 8").end().startBlock();
            b.startStatement().startCall("doEmitInstruction").tree(createInstructionConstant(model.popVariadicInstruction[variadicCount])).end(2);
            b.statement("elementCount -= 7");
            b.end();

            b.startIf().string("elementCount > 0").end().startBlock();
            b.startStatement().startCall("doEmitInstruction").startGroup().tree(createInstructionConstant(model.popVariadicInstruction[0])).string(" + elementCount").end(3);
            b.end();
            b.startStatement().startCall("doEmitInstruction").tree(createInstructionConstant(model.mergeVariadicInstruction)).end(2);
            b.end();

            b.statement("currentStackHeight -= count - 1");
            b.startIf().string("count == 0 && currentStackHeight > maxStackHeight").end().startBlock();
            b.statement("maxStackHeight = currentStackHeight");
            b.end();

            return ex;
        }

        private void buildEmitInstruction(CodeTreeBuilder b, InstructionModel instr, String... arguments) {
            boolean hasPositiveDelta = false;

            switch (instr.kind) {
                case BRANCH:
                case BRANCH_BACKWARD:
                case INSTRUMENTATION_ENTER:
                case INSTRUMENTATION_EXIT:
                case INSTRUMENTATION_LEAVE:
                case LOAD_LOCAL_MATERIALIZED:
                case THROW:
                case YIELD:
                case RETURN:
                case TRAP:
                    break;
                case BRANCH_FALSE:
                case POP:
                case STORE_LOCAL:
                case MERGE_CONDITIONAL:
                    b.statement("currentStackHeight -= 1");
                    break;
                case CUSTOM:
                    int delta = (instr.signature.isVoid ? 0 : 1) - instr.signature.valueCount;
                    if (delta != 0) {
                        b.statement("currentStackHeight += " + delta);
                        hasPositiveDelta = delta > 0;
                    }
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
                        b.statement("currentStackHeight -= 1");
                    } else {
                        // Stack: [..., value, convertedValue]
                        b.statement("currentStackHeight -= 2");
                    }
                    break;
                case DUP:
                case LOAD_ARGUMENT:
                case LOAD_CONSTANT:
                case LOAD_LOCAL:
                    hasPositiveDelta = true;
                    b.statement("currentStackHeight += 1");
                    break;
                case STORE_LOCAL_MATERIALIZED:
                    b.statement("currentStackHeight -= 2");
                    break;
                default:
                    throw new UnsupportedOperationException();
            }

            if (hasPositiveDelta) {
                b.startIf().string("currentStackHeight > maxStackHeight").end().startBlock();
                b.statement("maxStackHeight = currentStackHeight");
                b.end();
            }

            b.startStatement().startCall("doEmitInstruction");
            b.tree(createInstructionConstant(instr));
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

            // todo: check for superinstructions
        }

        private CodeExecutableElement createDoEmitSourceInfo() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "doEmitSourceInfo");
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "sourceIndex"));
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "start"));
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "length"));

            CodeTreeBuilder b = ex.createBuilder();

            b.startAssert().string("parseSources").end();

            b.startIf().string("sourceInfoIndex == 0 && start == -1").end().startBlock();
            b.returnStatement();
            b.end();

            // this is > 3 and not > 0 since we explicitly want to keep the very first entry, even
            // if the second has the same BCI, since that first one is the entire function source
            // section that we report
            b.startIf().string("sourceInfoIndex > 3 && (sourceInfo[sourceInfoIndex - 3] & 0xffff) == bci").end().startBlock();
            b.statement("sourceInfoIndex -= 3");
            b.end();

            b.startIf().string("sourceInfo.length == sourceInfoIndex").end().startBlock();
            b.statement("sourceInfo = Arrays.copyOf(sourceInfo, sourceInfo.length * 2)");
            b.end();

            b.statement("sourceInfo[sourceInfoIndex++] = (sourceIndex << 16) | bci");
            b.statement("sourceInfo[sourceInfoIndex++] = start");
            b.statement("sourceInfo[sourceInfoIndex++] = length");

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

            b.startIf().string("exHandlers.length <= exHandlerCount + 5").end().startBlock();
            b.statement("exHandlers = Arrays.copyOf(exHandlers, exHandlers.length * 2)");
            b.end();

            b.statement("int result = exHandlerCount");

            b.statement("exHandlers[exHandlerCount++] = startBci");
            b.statement("exHandlers[exHandlerCount++] = endBci");
            b.statement("exHandlers[exHandlerCount++] = handlerBci");
            b.statement("exHandlers[exHandlerCount++] = spStart");
            b.statement("exHandlers[exHandlerCount++] = exceptionLocal");

            b.statement("return result");

            return ex;
        }

        private CodeExecutableElement createDoEmitLeaves() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "doEmitLeaves");
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "targetSeq"));

            CodeTreeBuilder b = ex.createBuilder();

            b.startFor().string("int i = operationSp - 1; i >= 0; i--").end().startBlock();

            b.startIf().string("operationStack[i].sequenceNumber == targetSeq").end().startBlock();
            b.returnStatement();
            b.end();

            b.startSwitch().string("operationStack[i].operation").end().startBlock();

            for (OperationModel op : model.getOperations()) {
                switch (op.kind) {
                    case FINALLY_TRY:
                    case FINALLY_TRY_NO_EXCEPT:
                        b.startCase().tree(createOperationConstant(op)).end();
                }
            }
            b.startBlock();
            emitCastOperationData(b, "FinallyTryData", "i");

            b.statement("FinallyTryContext ctx = (FinallyTryContext) operationData.finallyTryContext");
            b.startIf().string("ctx.handlerIsSet()").end().startBlock();
            b.statement("doEmitFinallyHandler(ctx)");
            b.end();

            b.statement("break");
            b.end();

            b.end();

            b.end();

            return ex;
        }

        private CodeExecutableElement createAllocateNode() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(int.class), "allocateNode");
            CodeTreeBuilder b = ex.createBuilder();

            b.startIf().string("inFinallyTryHandler(finallyTryContext)").end().startBlock();
            b.lineComment("We allocate nodes later when the finally block is emitted.");
            b.startReturn().string("-1").end();
            b.end();

            b.startReturn();
            b.string("numNodes++");
            b.end();

            return ex;
        }

        private CodeExecutableElement createAllocateBranchProfile() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(int.class), "allocateBranchProfile");
            CodeTreeBuilder b = ex.createBuilder();

            b.startIf().string("inFinallyTryHandler(finallyTryContext)").end().startBlock();
            b.lineComment("We allocate nodes later when the finally block is emitted.");
            b.startReturn().string("-1").end();
            b.end();

            b.startReturn();
            b.string("numConditionalBranches++");
            b.end();

            return ex;
        }

        private CodeExecutableElement createInFinallyTryHandler() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(boolean.class), "inFinallyTryHandler");
            ex.addParameter(new CodeVariableElement(finallyTryContext.asType(), "context"));
            CodeTreeBuilder b = ex.createBuilder();

            b.startReturn();
            b.string("context != null && (!context.handlerIsSet() || inFinallyTryHandler(context.parentContext))");
            b.end();

            return ex;
        }

        private CodeExecutableElement createGetFinallyTryHandlerSequenceNumber() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(int.class), "getFinallyTryHandlerSequenceNumber");
            ex.addParameter(new CodeVariableElement(finallyTryContext.asType(), "context"));
            CodeTreeBuilder b = ex.createBuilder();

            b.startReturn();
            b.string("inFinallyTryHandler(context) ? context.finallyTrySequenceNumber : -1");
            b.end();

            return ex;
        }

        private static String writeBc(String index, String value) {
            return String.format("ACCESS.shortArrayWrite(bc, %s, %s)", index, value);
        }

        private static String readHandlerBc(String index) {
            return String.format("ACCESS.shortArrayRead(handlerBc, %s)", index);
        }

        // Finally handler code gets emitted in multiple locations. When a branch target is inside a
        // finally handler, the instruction referencing it needs to be remembered so that we can
        // relocate the target each time we emit the instruction.
        // This helper should only be used for a local branch within the same operation (i.e., the
        // "defining context" of the branch target is the current finallyTryContext).
        // For potentially non-local branches (i.e. branches to outer operations) we must instead
        // determine the context that defines the branch target.
        private void emitFinallyRelativeBranchCheck(CodeTreeBuilder b, int offset) {
            b.startIf().string("inFinallyTryHandler(finallyTryContext)").end().startBlock();
            b.statement("finallyTryContext.finallyRelativeBranches.add(bci + " + offset + ")");
            b.end();
        }

        private CodeExecutableElement createReparseConstructor() {
            CodeExecutableElement ctor = new CodeExecutableElement(Set.of(PRIVATE), null, "Builder");
            ctor.addParameter(new CodeVariableElement(bytecodeRootNodesImpl.asType(), "nodes"));
            ctor.addParameter(new CodeVariableElement(context.getType(boolean.class), "parseBytecodes"));
            ctor.addParameter(new CodeVariableElement(context.getType(int.class), "tags"));
            ctor.addParameter(new CodeVariableElement(context.getType(int.class), "instrumentations"));
            ctor.addParameter(new CodeVariableElement(context.getType(boolean.class), "parseSources"));

            CodeTreeBuilder javadoc = ctor.createDocBuilder();
            javadoc.startJavadoc();
            javadoc.string("Constructor for reparsing.");
            javadoc.end();

            CodeTreeBuilder b = ctor.createBuilder();

            b.statement("this.nodes = nodes");
            b.statement("this.isReparse = true");
            b.statement("this.parseBytecodes = parseBytecodes");
            b.statement("this.tags = tags");
            b.statement("this.instrumentations = instrumentations");
            b.statement("this.parseSources = parseSources");
            b.statement("this.sources = parseSources ? new ArrayList<>(4) : null");
            b.statement("this.builtNodes = new ArrayList<>()");

            return ctor;
        }

        private CodeExecutableElement createParseConstructor() {
            CodeExecutableElement ctor = new CodeExecutableElement(Set.of(PRIVATE), null, "Builder");
            ctor.addParameter(new CodeVariableElement(bytecodeRootNodesImpl.asType(), "nodes"));
            ctor.addParameter(new CodeVariableElement(types.BytecodeConfig, "config"));

            CodeTreeBuilder javadoc = ctor.createDocBuilder();
            javadoc.startJavadoc();
            javadoc.string("Constructor for initial parses.");
            javadoc.end();

            CodeTreeBuilder b = ctor.createBuilder();

            b.statement("this.nodes = nodes");
            b.statement("this.isReparse = false");
            b.statement("this.tags = encodeTags(getAddTags(config))");
            b.statement("this.instrumentations = encodeInstrumentation(getAddInstrumentations(config)) & ~encodeInstrumentation(getRemoveInstrumentations(config))");
            b.statement("this.parseBytecodes = true");
            b.statement("this.parseSources = isAddSource(config)");
            b.statement("this.sources = parseSources ? new ArrayList<>(4) : null");
            b.statement("this.builtNodes = new ArrayList<>()");

            return ctor;
        }

        private CodeExecutableElement createGetAllInstrumentations() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeBuilder, "getAllInstrumentations");
            addOverride(ex);

            CodeTreeBuilder b = ex.createBuilder();
            // TODO: implement
            b.startReturn();
            b.string("EMPTY_ARRAY");
            b.end();

            return ex;
        }

        private CodeExecutableElement createGetAllTags() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeBuilder, "getAllTags");
            addOverride(ex);

            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn();
            if (model.getProvidedTags().isEmpty()) {
                b.string("EMPTY_ARRAY");
            } else {
                b.startNewArray(arrayOf(context.getDeclaredType(Class.class)), null);
                for (TypeMirror mir : model.getProvidedTags()) {
                    b.typeLiteral(mir);
                }
                b.end();
            }
            b.end();

            return ex;
        }

        private void buildContextSensitiveFieldInitializer(CodeTreeBuilder b) {
            b.statement("bc = new short[32]");
            b.statement("bci = 0");
            b.statement("currentStackHeight = 0");
            b.statement("maxStackHeight = 0");
            b.startIf().string("parseSources").end().startBlock();
            b.statement("sourceInfo = new int[15]");
            b.statement("sourceInfoIndex = 0");
            b.end();
            b.statement("exHandlers = new int[10]");
            b.statement("exHandlerCount = 0");
            b.statement("unresolvedLabels = new HashMap<>()");
            if (model.enableTracing) {
                b.statement("basicBlockBoundary = new boolean[33]");
            }
            if (model.enableYield) {
                b.statement("continuationLocations = new ArrayList<>()");
            }
        }
    }

    class BytecodeRootNodesImplFactory {
        private CodeTypeElement create() {
            bytecodeRootNodesImpl.setSuperClass(generic(types.BytecodeRootNodes, model.templateType.asType()));
            bytecodeRootNodesImpl.setEnclosingElement(bytecodeNodeGen);
            bytecodeRootNodesImpl.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(Object.class), "VISIBLE_TOKEN")).createInitBuilder().string("TOKEN");

            bytecodeRootNodesImpl.add(new CodeVariableElement(Set.of(PRIVATE), type(int.class), "instrumentations"));
            bytecodeRootNodesImpl.add(new CodeVariableElement(Set.of(PRIVATE), type(int.class), "tags"));
            bytecodeRootNodesImpl.add(new CodeVariableElement(Set.of(PRIVATE), type(boolean.class), "sources"));

            bytecodeRootNodesImpl.add(createConstructor());
            bytecodeRootNodesImpl.add(createReparseImpl());
            bytecodeRootNodesImpl.add(createSetNodes());
            bytecodeRootNodesImpl.add(createGetParserImpl());

            if (model.enableSerialization) {
                bytecodeRootNodesImpl.add(createSerialize());
            }

            return bytecodeRootNodesImpl;
        }

        private CodeExecutableElement createConstructor() {
            CodeExecutableElement ctor = new CodeExecutableElement(null, "BytecodeRootNodesImpl");
            ctor.addParameter(new CodeVariableElement(parserType, "generator"));

            ctor.createBuilder().statement("super(generator)");
            return ctor;
        }

        private CodeExecutableElement createReparseImpl() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeRootNodes, "reparseImpl");
            addOverride(ex);
            ex.getModifiers().add(Modifier.SYNCHRONIZED);
            mergeSuppressWarnings(ex, "hiding");
            ex.renameArguments("config");
            CodeTreeBuilder b = ex.createBuilder();

            b.declaration(parserType, "parser", "getParserImpl()");
            b.declaration(type(boolean.class), "addSource", "isAddSource(config)");
            b.declaration(type(int.class), "addInstrumentation", "encodeInstrumentation(getAddInstrumentations(config))");
            b.declaration(type(int.class), "removeInstrumentation", "encodeInstrumentation(getRemoveInstrumentations(config))");
            b.declaration(type(int.class), "addTags", "encodeTags(getAddTags(config))");

            b.statement("boolean oldSources = this.sources");
            b.statement("int oldTags = this.tags");
            b.statement("int oldInstrumentations = this.instrumentations");

            b.statement("int newTags = oldTags | addTags");
            b.statement("int newInstrumentations = (oldInstrumentations | addInstrumentation) & ~removeInstrumentation");
            b.statement("boolean newSources = oldSources || addSource");
            b.statement("boolean needsBytecodeReparse = newInstrumentations != oldInstrumentations || newTags != oldTags");
            b.statement("boolean needsSourceReparse = newSources != oldSources || (needsBytecodeReparse && newSources)");

            b.startIf().string("!needsBytecodeReparse && !needsSourceReparse").end().startBlock();
            b.statement("return false");
            b.end();

            // When we reparse, we add metadata to the existing nodes. The builder gets them here.
            b.declaration(builder.getSimpleName().toString(), "builder",
                            b.create().startNew(builder.getSimpleName().toString()).string("this").string("needsBytecodeReparse").string("newTags").string("newInstrumentations").string(
                                            "needsSourceReparse").end().build());

            b.startFor().type(model.templateType.asType()).string(" node : nodes").end().startBlock();
            b.startStatement().startCall("builder.builtNodes.add");
            b.startGroup().cast(bytecodeNodeGen.asType()).string("node").end();
            b.end(2);
            b.end(2);

            b.startStatement().startCall("parser", "parse").string("builder").end(2);
            b.startStatement().startCall("builder", "finish").end(2);

            b.statement("this.tags = newTags");
            b.statement("this.instrumentations = newInstrumentations");
            b.statement("this.sources = newSources");

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

        private CodeExecutableElement createSerialize() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeRootNodes, "serialize");
            ex.renameArguments("buffer", "callback");
            addOverride(ex);
            CodeTreeBuilder b = ex.createBuilder();

            b.startStatement();
            b.startStaticCall(bytecodeNodeGen.asType(), "serialize");
            b.string("buffer");
            b.string("callback");
            b.string("getParserImpl()");
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
            type.add(compFinal(1, new CodeVariableElement(Set.of(FINAL), context.getType(int[].class), "sourceInfo")));
            type.add(new CodeVariableElement(Set.of(FINAL), generic(type(List.class), types.Source), "sources"));

            for (ExecutableElement superConstructor : ElementFilter.constructorsIn(ElementUtils.castTypeElement(types.BytecodeNode).getEnclosedElements())) {
                CodeExecutableElement constructor = CodeExecutableElement.cloneNoAnnotations(superConstructor);
                constructor.setReturnType(null);
                constructor.setSimpleName(type.getSimpleName());
                constructor.getParameters().remove(0);
                constructor.addParameter(new CodeVariableElement(arrayOf(type(short.class)), "bc"));
                constructor.addParameter(new CodeVariableElement(arrayOf(type(Object.class)), "constants"));
                constructor.addParameter(new CodeVariableElement(arrayOf(type(int.class)), "handlers"));
                constructor.addParameter(new CodeVariableElement(arrayOf(type(int.class)), "sourceInfo"));
                constructor.addParameter(new CodeVariableElement(generic(type(List.class), types.Source), "sources"));

                CodeTreeBuilder b = constructor.createBuilder();
                b.startStatement().startSuperCall().string("BytecodeRootNodesImpl.VISIBLE_TOKEN").end().end();
                b.statement("this.constants = constants");
                b.statement("this.bytecodes = bc");
                b.statement("this.handlers = handlers");
                b.statement("this.sourceInfo = sourceInfo");
                b.statement("this.sources = sources");
                type.add(constructor);
                break;
            }

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
            CodeExecutableElement addSource = type.add(new CodeExecutableElement(Set.of(ABSTRACT), type.asType(), "addSource"));
            addSource.addParameter(new CodeVariableElement(arrayOf(type(int.class)), "newSourceInfo"));
            addSource.addParameter(new CodeVariableElement(generic(type(List.class), types.Source), "newSources"));

            type.add(new CodeExecutableElement(Set.of(ABSTRACT), type.asType(), "cloneUninitialized"));

            type.add(new CodeExecutableElement(Set.of(ABSTRACT), arrayOf(types.Node), "getCachedNodes"));
            type.add(new CodeExecutableElement(Set.of(ABSTRACT), arrayOf(type(int.class)), "getBranchProfiles"));

            // Define methods for introspecting the bytecode and source.
            type.add(createGetIntrospectionData());
            type.add(createParseInstruction());
            type.add(createGetSourceSection());
            type.add(createFindSourceLocation());
            type.add(createFindInstruction());

            return type;
        }

        private CodeExecutableElement createFindSourceLocation() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeNode, "findSourceLocation");
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

            b.statement("int i = 0");

            b.startWhile().string("i < info.length && (info[i] & 0xffff) <= bci").end().startBlock();
            b.statement("i += 3");
            b.end();

            b.startIf().string("i == 0").end().startBlock();
            b.returnNull();
            b.end();

            b.statement("i -= 3");

            b.startIf().string("info[i + 1] == -1").end().startBlock();
            b.returnNull();
            b.end();

            b.statement("return localSources.get((info[i] >> 16) & 0xffff).createSection(info[i + 1], info[i + 2])");

            return ex;
        }

        private CodeExecutableElement createGetSourceSection() {
            CodeExecutableElement ex = GeneratorUtils.override(types.Node, "getSourceSection");
            ex.getAnnotationMirrors().add(new CodeAnnotationMirror(types.CompilerDirectives_TruffleBoundary));
            CodeTreeBuilder b = ex.createBuilder();

            b.declaration(arrayOf(type(int.class)), "info", "this.sourceInfo");
            b.declaration(generic(type(List.class), types.Source), "localSources", "this.sources");
            b.startIf().string("info == null").end().startBlock();
            b.declaration(type.asType(), "newNode", "getRoot().ensureSources()");
            b.statement("info = newNode.sourceInfo");
            b.statement("localSources = newNode.sources");
            b.end();

            b.startIf().string("info.length == 0").end().startBlock();
            b.returnNull();
            b.end();

            b.startReturn().string("localSources.get((info[0] >> 16) & 0xffff).createSection(info[1], info[2])").end();

            return ex;
        }

        private CodeExecutableElement createGetIntrospectionData() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), types.BytecodeIntrospection, "getIntrospectionData");
            CodeTreeBuilder b = ex.createBuilder();

            b.declaration(arrayOf(type(short.class)), "bc", "this.bytecodes");
            b.declaration(generic(type(List.class), type(Object[].class)), "instructions", "new ArrayList<>()");

            b.declaration(type(int[].class), "bci", "new int[1]");
            b.startTryBlock();

            b.startFor().string("; bci[0] < bc.length;").end().startBlock();
            b.startStatement().startCall("instructions", "add").startCall("parseInstruction").string("bci[0]").string(readBc("bci[0]")).string("bci").end().end().end();
            b.end();

            b.end().startCatchBlock(type(Throwable.class), "e");
            b.statement("StringBuilder b = new StringBuilder()");
            b.declaration("String", "sep", "\"\"");

            b.startFor().string("Object[] instruction : instructions").end().startBlock();
            b.statement("b.append(sep)");
            b.startStatement().startCall("b.append").startGroup().startNew(types.Instruction).string("instruction").end().string(".toString()").end(3);
            b.statement("sep = \"\\n    \"");
            b.end();

            b.startThrow().startNew(type(IllegalStateException.class));
            b.startGroup();
            b.doubleQuote("Error parsing instructions at ").string(" + bci[0] + ").doubleQuote(". Parsed instructions so far: \\n    ").string(" + b.toString()");
            b.end();
            b.string("e");
            b.end().end();
            b.end(); // catch block

            b.statement("Object[] exHandlersInfo = new Object[handlers.length / 5]");

            b.startFor().string("int idx = 0; idx < exHandlersInfo.length; idx++").end().startBlock();
            b.statement("exHandlersInfo[idx] = new Object[]{ handlers[idx * 5], handlers[idx * 5 + 1], handlers[idx * 5 + 2], handlers[idx * 5 + 4] }");
            b.end();

            b.declaration(generic(type(List.class), type(Object[].class)), "sourceData", "new ArrayList<>()");
            b.startIf().string("sourceInfo != null").end().startBlock();
            b.startFor().string("int idx = 0; idx < sourceInfo.length; idx += 3").end().startBlock();

            // we encode ranges with no SourceSection using (-1, -1)
            b.startIf().string("sourceInfo[idx + 1] == -1").end().startBlock();
            b.statement("continue");
            b.end();

            b.statement("int startIndex = sourceInfo[idx] & 0xffff");
            b.statement("int endIndex = (idx + 3 == sourceInfo.length) ? bc.length : sourceInfo[idx + 3] & 0xffff");
            b.declaration(types.SourceSection, "section", "this.sources.get((sourceInfo[idx] >> 16) & 0xffff).createSection(sourceInfo[idx + 1], sourceInfo[idx + 2])");
            b.statement("sourceData.add(new Object[]{startIndex, endIndex, section})");
            b.end();
            b.end();

            b.startReturn().startNew(types.BytecodeIntrospection);
            b.string("new Object[]{0, instructions.toArray(), exHandlersInfo, sourceData.toArray()}");
            b.end(2);

            return ex;
        }

        private CodeExecutableElement createFindInstruction() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeNode, "findInstruction");
            ex.renameArguments("bci");
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn();
            b.startNew(types.Instruction);
            b.startCall("parseInstruction").string("bci").string("this.bytecodes[bci]").string("null").end();
            b.end();
            b.end();
            return ex;
        }

        private CodeExecutableElement createParseInstruction() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(), type(Object[].class), "parseInstruction");
            ex.addParameter(new CodeVariableElement(type(int.class), "bci"));
            ex.addParameter(new CodeVariableElement(type(int.class), "operand"));
            ex.addParameter(new CodeVariableElement(type(int[].class), "nextBci"));
            CodeTreeBuilder b = ex.createBuilder();
            b.declaration(arrayOf(type(short.class)), "bc", "this.bytecodes");
            b.declaration(arrayOf(types.Node), "cachedNodes", "getCachedNodes()");
            b.declaration(arrayOf(type(int.class)), "branchProfiles", "getBranchProfiles()");
            b.declaration(types.BytecodeLocation, "location", "findLocation(bci)");

            b.startSwitch().string("operand").end().startBlock();

            for (InstructionModel instr : model.getInstructions()) {
                b.startCase().tree(createInstructionConstant(instr)).end().startCaseBlock();

                b.startIf().string("nextBci != null").end().startBlock();
                b.statement("nextBci[0] = bci + " + instr.getInstructionLength());
                b.end();

                // instruction data array
                b.startReturn().startNewArray(arrayOf(context.getType(Object.class)), null);
                b.string("location");
                b.doubleQuote(instr.name);
                b.string("new short[] {" + instr.getId() + "}");

                // arguments array
                b.startNewArray(arrayOf(context.getType(Object.class)), null);

                for (InstructionImmediate immediate : instr.getImmediates()) {
                    // argument data array
                    b.startNewArray(arrayOf(context.getType(Object.class)), null);
                    b.staticReference(types.Argument_ArgumentType, switch (immediate.kind()) {
                        case CONSTANT -> "CONSTANT";
                        case BYTECODE_INDEX -> "BYTECODE_INDEX";
                        case INTEGER, LOCAL_SETTER, LOCAL_SETTER_RANGE_LENGTH, LOCAL_SETTER_RANGE_START -> "INTEGER";
                        case NODE_PROFILE -> "NODE_PROFILE";
                        case BRANCH_PROFILE -> "BRANCH_PROFILE";
                        default -> throw new AssertionError("Unexpected kind");
                    });
                    b.doubleQuote(immediate.name());
                    String readImmediate = readBc("bci + " + immediate.offset());
                    switch (immediate.kind()) {
                        case BYTECODE_INDEX:
                        case INTEGER:
                        case LOCAL_SETTER:
                        case LOCAL_SETTER_RANGE_LENGTH:
                        case LOCAL_SETTER_RANGE_START:
                            b.string(readImmediate);
                            break;
                        case CONSTANT:
                            b.string(readConst(readImmediate));
                            break;
                        case NODE_PROFILE:
                            b.startGroup();
                            b.string("cachedNodes != null ? ");
                            b.string(readNodeProfile(types.Node, CodeTreeBuilder.singleString(readImmediate)));
                            b.string(" : null");
                            b.end();
                            break;
                        case BRANCH_PROFILE:
                            b.string("new int[] {" + readImmediate + ", " + readBranchProfile(readImmediate + " * 2") + ", " + readBranchProfile(readImmediate + " * 2 + 1") + "}");
                            break;
                        default:
                            throw new AssertionError("Unexpected kind");
                    }
                    b.end(); // Object[]

                }
                b.end(); // Object[]

                b.end(); // Object[]
                b.end(); // return

                b.end();
            }

            b.caseDefault().startCaseBlock();
            b.tree(GeneratorUtils.createShouldNotReachHere(b.create().doubleQuote("Invalid BCI at index: ").string(" + bci").build()));
            b.end();

            b.end();

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
                type.add(createLoadConstantCompiled());
            } else if (tier.isCached()) {
                type.add(createCachedConstructor());
                type.add(compFinal(1, new CodeVariableElement(Set.of(PRIVATE, FINAL), arrayOf(types.Node), "cachedNodes_")));
                type.add(compFinal(1, new CodeVariableElement(Set.of(PRIVATE, FINAL), arrayOf(context.getType(int.class)), "branchProfiles_")));
                type.add(createLoadConstantCompiled());
                // Define the members required to support OSR.
                type.getImplements().add(types.BytecodeOSRNode);
                type.addAll(new OSRMembersFactory().create());
            } else if (tier.isUninitialized()) {
                type.add(GeneratorUtils.createConstructorUsingFields(Set.of(), type));
            }

            type.add(createSetUncachedThreshold());

            type.add(createGetTier());

            // uninitialized does not need a copy constructor as the default constructor is already
            // copying.
            if (!tier.isUninitialized()) {
                type.add(createCopyConstructor());
            }
            type.add(createToCached());
            type.add(createAddSource());
            type.add(createCloneUninitialized());
            type.add(createGetCachedNodes());
            type.add(createGetBranchProfiles());
            type.add(createFindBytecodeIndex1());
            type.add(createFindBytecodeIndex2());
            type.add(createFindBytecodeIndexOfOperationNode());

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
                b.startReturn();
                b.startCall("frameInstance", "getFrame");
                b.staticReference(types.FrameInstance_FrameAccess, "READ_ONLY");
                b.end();
                b.string(".getInt(" + BCI_IDX + ")");
                b.end();
            } else {
                b.startReturn().string("-1").end();
            }

            return ex;
        }

        private CodeExecutableElement createFindBytecodeIndex2() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeNode, "findBytecodeIndex", 2);
            ex.renameArguments("frame", "node");
            CodeTreeBuilder b = ex.createBuilder();
            b.startAssert().string("node == null || node.getParent() == this : ").doubleQuote("Passed node must be an operation node of the same bytecode node.").end();
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

        private CodeExecutableElement createCloneUninitialized() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement((DeclaredType) abstractBytecodeNode.asType(), "cloneUninitialized");
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn();
            b.startNew(InterpreterTier.CACHED.friendlyName + "BytecodeNode");
            b.string("getRoot()");
            for (VariableElement var : ElementFilter.fieldsIn(abstractBytecodeNode.getEnclosedElements())) {
                b.string("this.", var.getSimpleName().toString());
            }
            b.end();
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
                    b.string("getRoot()");
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
                String name = var.getSimpleName().toString();
                ex.addParameter(new CodeVariableElement(var.asType(), name));
                b.statement("this.", name, " = ", name);
            }

            return ex;
        }

        private CodeExecutableElement createAddSource() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement((DeclaredType) abstractBytecodeNode.asType(), "addSource");
            ex.renameArguments("newSourceInfo", "newSources");
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn();
            b.startNew(type.asType());
            b.string("this.bytecodes");
            b.string("this.constants");
            b.string("this.handlers");
            b.string("newSourceInfo");
            b.string("newSources");
            for (VariableElement var : ElementFilter.fieldsIn(type.getEnclosedElements())) {
                b.string("this.", var.getSimpleName().toString());
            }
            b.end();
            b.end();
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
            b.tree(createNeverPartOfCompilation());
            if (!tier.isCached()) {
                mergeSuppressWarnings(ex, "static-method");
                b.startReturn().string("-1").end();
                return ex;
            }
            b.declaration(arrayOf(types.Node), "localNodes", "this.cachedNodes_");
            b.declaration(arrayOf(type(short.class)), "bc", "this.bytecodes");
            b.statement("int bci = 0");
            b.string("loop: ").startWhile().string("bci < bc.length").end().startBlock();
            b.declaration(context.getType(int.class), "currentBci", "bci");
            b.declaration(context.getType(int.class), "nodeIndex");
            b.startSwitch().string(readBc("bci")).end().startBlock();

            Map<Boolean, List<InstructionModel>> instructionsGroupedByHasNode = model.getInstructions().stream().collect(Collectors.partitioningBy(InstructionModel::hasNodeImmediate));
            Map<Integer, List<InstructionModel>> nodelessGroupedByLength = instructionsGroupedByHasNode.get(false).stream().collect(Collectors.groupingBy(InstructionModel::getInstructionLength));
            Map<Integer, List<InstructionModel>> nodedGroupedByLength = instructionsGroupedByHasNode.get(true).stream().collect(Collectors.groupingBy(InstructionModel::getInstructionLength));

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
            // We group them by size to simplify the generated code.
            for (Map.Entry<Integer, List<InstructionModel>> entry : nodedGroupedByLength.entrySet()) {
                for (InstructionModel instr : entry.getValue()) {
                    b.startCase().tree(createInstructionConstant(instr)).end();
                }
                // NB: this relies on all custom instructions encoding their node as the last
                // immediate.
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

            return ex;

        }

        private CodeExecutableElement createCachedConstructor() {

            record CachedInitializationKey(int instructionLength, List<InstructionImmediate> immediates, String nodeName) {
                CachedInitializationKey(InstructionModel instr) {
                    this(instr.getInstructionLength(), instr.getImmediates().stream().filter((i) -> needsCachedInitialization(i)).toList(), cachedDataClassName(instr));
                }
            }

            CodeExecutableElement ex = GeneratorUtils.createConstructorUsingFields(Set.of(), type);
            ex.getParameters().add(0, new CodeVariableElement(bytecodeNodeGen.asType(), "$root"));

            TypeMirror nodeArrayType = new ArrayCodeTypeMirror(types.Node);

            CodeTreeBuilder b = ex.appendBuilder();

            b.tree(createNeverPartOfCompilation());
            b.declaration(nodeArrayType, "result", "new Node[$root.numNodes]");
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

            b.statement("int bci = startState & 0xffff");
            b.statement("int sp = (startState >> 16) & 0xffff");
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
                        b.statement("bci = " + readBc("bci + 1"));
                        b.statement("continue loop");
                        break;
                    case BRANCH_BACKWARD:
                        if (tier.isUncached()) {
                            b.startIf().string("--uncachedExecuteCount <= 0").end().startBlock();
                            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                            b.statement("$root.transitionToCached()");
                            b.statement("return (sp << 16) | " + readBc("bci + 1"));
                            b.end();
                        } else {
                            emitReportLoopCount(b, CodeTreeBuilder.createBuilder().string("++loopCounter.value >= ").staticReference(loopCounter.asType(), "REPORT_LOOP_STRIDE").build(), true);

                            b.startIf().startStaticCall(types.CompilerDirectives, "inInterpreter").end(1).string(" && ") //
                                            .startStaticCall(types.BytecodeOSRNode, "pollOSRBackEdge").string("this").end(2).startBlock();

                            b.startAssign("Object osrResult");
                            b.startStaticCall(types.BytecodeOSRNode, "tryOSR");
                            b.string("this");
                            b.string("(sp << 16) | " + readBc("bci + 1")); // target
                            b.string("null"); // interpreterState
                            b.string("null"); // beforeTransfer
                            b.string("frame"); // parentFrame
                            b.end(2);

                            b.startIf().string("osrResult != null").end().startBlock();
                            b.statement(setFrameObject("sp", "osrResult"));
                            b.statement("sp++");
                            emitReturnTopOfStack(b);
                            b.end();

                            b.end();
                        }
                        b.statement("bci = + " + readBc("bci + 1"));
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
                        b.statement("bci = " + readBc("bci + 1"));
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
                    case INSTRUMENTATION_ENTER:
                    case INSTRUMENTATION_EXIT:
                    case INSTRUMENTATION_LEAVE:
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
                        if (model.usesBoxingElimination() && !ElementUtils.isObject(returnType)) {
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
                        if (model.usesBoxingElimination()) {
                            if (instr.isQuickening()) {
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
                        } else {
                            b.statement(clearFrame("frame", "sp - 1"));
                        }
                        b.statement("sp -= 1");
                        break;
                    case DUP:
                        b.statement(copyFrameSlot("sp - 1", "sp"));
                        b.statement("sp += 1");
                        break;
                    case TRAP:
                        emitThrowAssertionError(b, "\"Control reached past the end of the bytecode.\"");
                        break;
                    case RETURN:
                        storeBciInFrameIfNecessary(b);
                        emitBeforeReturnProfiling(b);
                        emitReturnTopOfStack(b);
                        break;
                    case LOAD_LOCAL:
                        if (model.usesBoxingElimination()) {
                            if (instr.isQuickening()) {
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
                        } else {
                            b.statement(setFrameObject("sp", getFrameObject(localFrame(), readBc("bci + 1"))));
                        }
                        b.statement("sp += 1");
                        break;
                    case LOAD_LOCAL_MATERIALIZED:
                        String materializedFrame = "((VirtualFrame) " + getFrameObject("sp - 2)");
                        if (model.usesBoxingElimination()) {
                            if (instr.isQuickening()) {
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
                        } else {
                            b.statement(setFrameObject("sp - 1", getFrameObject("(VirtualFrame) " + getFrameObject("sp - 1"), readBc("bci + 1"))));
                        }
                        break;
                    case STORE_LOCAL:
                        if (model.usesBoxingElimination()) {
                            if (instr.isQuickening()) {
                                b.startStatement();
                                b.startCall(lookupDoStoreLocal(instr).getSimpleName().toString());
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
                                b.startCall(lookupDoSpecializeStoreLocal(instr).getSimpleName().toString());
                                if (model.specializationDebugListener) {
                                    b.string("this");
                                }
                                b.string("frame");
                                if (model.enableYield) {
                                    b.string("localFrame");
                                }
                                b.string("bc").string("bci").string("sp");
                                startGetFrameUnsafe(b, "frame", type(Object.class)).string("sp - 1").end();
                                b.end();
                                b.end();
                            }
                        } else {
                            b.startStatement();
                            startSetFrame(b, type(Object.class)).string(localFrame()).string(readBc("bci + 1")).string(getFrameObject("sp - 1")).end();
                            b.end();
                        }
                        b.statement(clearFrame("frame", "sp - 1"));
                        b.statement("sp -= 1");
                        break;
                    case STORE_LOCAL_MATERIALIZED:
                        materializedFrame = "((VirtualFrame) " + getFrameObject("sp - 2)");

                        if (model.usesBoxingElimination()) {
                            if (instr.isQuickening()) {
                                b.startStatement();
                                b.startCall(lookupDoStoreLocal(instr).getSimpleName().toString());
                                if (model.specializationDebugListener) {
                                    b.string("this");
                                }
                                b.string("frame").string(materializedFrame).string("bc").string("bci").string("sp");
                                b.end();
                                b.end();
                            } else {
                                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                                b.startStatement();
                                b.startCall(lookupDoSpecializeStoreLocal(instr).getSimpleName().toString());
                                if (model.specializationDebugListener) {
                                    b.string("this");
                                }
                                b.string("frame").string(materializedFrame).string("bc").string("bci").string("sp");
                                startGetFrameUnsafe(b, localFrame(), type(Object.class)).string("sp - 1").end();
                                b.end();
                                b.end();
                            }
                        } else {
                            b.statement(materializedFrame + ".setObject(" + readBc("bci + 1") + ", " + getFrameObject("sp - 1") + ")");
                            b.statement(clearFrame("frame", "sp - 1"));
                            b.statement(clearFrame("frame", "sp - 2"));
                        }

                        b.statement("sp -= 2");
                        break;
                    case MERGE_CONDITIONAL:
                        if (!model.usesBoxingElimination()) {
                            throw new AssertionError("Merge.conditional only supports boxing elimination enabled.");
                        }
                        if (instr.isQuickening()) {
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
                        b.statement(setFrameObject("sp - 1", "((ContinuationLocation) " + readConst(readBc("bci + 1")) + ").createResult(localFrame, " + getFrameObject("sp - 1") + ")"));
                        emitReturnTopOfStack(b);
                        break;
                    case STORE_NULL:
                        b.statement(setFrameObject("sp", "null"));
                        b.statement("sp += 1");
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
                    case CUSTOM:
                        results.add(buildCustomInstructionExecute(b, instr));
                        break;
                    case SUPERINSTRUCTION:
                        // not implemented yet
                        break;
                    default:
                        throw new UnsupportedOperationException("not implemented: " + instr.kind);
                }
                if (!instr.isControlFlow()) {
                    b.statement("bci += " + instr.getInstructionLength());
                    b.statement("continue loop");
                }
                b.end();
            }

            b.end(); // switch

            b.end(); // try

            DeclaredType throwable = context.getDeclaredType("java.lang.Throwable");
            DeclaredType abstractTruffleException = types.AbstractTruffleException;
            DeclaredType controlFlowException = types.ControlFlowException;
            b.startCatchBlock(throwable, "throwable");

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
            b.declaration(abstractTruffleException, "ex");
            b.startIf().startGroup().string("throwable instanceof ").type(abstractTruffleException).string(" ate").end(2).startBlock();
            b.startAssign("ex").string("ate").end();
            b.end();

            if (model.interceptControlFlowException == null && model.interceptInternalException == null) {
                // Special case: no handlers for non-Truffle exceptions. Just rethrow.
                b.startElseBlock();
                b.startThrow().string("sneakyThrow(throwable)").end();
                b.end();
            } else {
                if (model.interceptControlFlowException != null) {
                    // @formatter:off
                    b.startElseIf().startGroup().string("throwable instanceof ").type(controlFlowException).string(" cfe").end(2).startBlock();
                        b.startTryBlock();
                            b.startAssign("Object result").startCall("$root", model.interceptControlFlowException).string("cfe").string("frame").string("bci").end(2);
                            emitBeforeReturnProfiling(b);
                            // There may not be room above the sp. Just use the first stack slot.
                            b.statement(setFrameObject("$root.numLocals", "result"));
                            b.startAssign("sp").string("$root.numLocals + 1").end();
                            emitReturnTopOfStack(b);
                        b.end().startCatchBlock(controlFlowException, "rethrownCfe");
                            b.startThrow().string("rethrownCfe").end();
                        b.end().startCatchBlock(abstractTruffleException, "ate");
                            b.statement("ex = ate");
                        b.end().startCatchBlock(throwable, "e");
                            b.startTryBlock();
                                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                                b.startThrow().string("sneakyThrow(");
                                if (model.interceptInternalException != null) {
                                    b.startCall("$root", model.interceptInternalException).string("e").string("bci").end();
                                } else {
                                    b.string("e");
                                }
                                b.string(")").end();
                            b.end().startCatchBlock(abstractTruffleException, "ate");
                                b.startAssign("ex").string("ate").end();
                            b.end();

                        b.end();
                    b.end();
                    // @formatter:on
                }
                // @formatter:off
                b.startElseBlock();
                    b.startTryBlock();
                        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                        if (model.interceptInternalException != null) {
                            if (model.interceptControlFlowException == null) {
                                // If there was no handler, we need to ensure throwable is not a ControlFlowException
                                b.startIf().string("!(throwable instanceof ").type(controlFlowException).string(")").end().startBlock();
                            }
                            b.startAssign("throwable").startCall("$root", model.interceptInternalException).string("throwable").string("bci").end(2);
                            if (model.interceptControlFlowException == null) {
                                // If there was no handler, we need to ensure throwable is not a ControlFlowException
                                b.end();
                            }
                        }
                        b.startThrow().startCall("sneakyThrow").string("throwable").end(2);
                    b.end().startCatchBlock(abstractTruffleException, "ate");
                        b.startAssign("ex").string("ate").end();
                    b.end();
                b.end();
                // @formatter:on
            }

            if (model.interceptTruffleException != null) {
                b.startAssign("ex").startCall("$root", model.interceptTruffleException).string("ex").string(localFrame()).string("bci").end(2);
            }

            b.statement("int[] localHandlers = this.handlers");
            b.startFor().string("int idx = 0; idx < localHandlers.length; idx += 5").end().startBlock();

            b.startIf().string("localHandlers[idx] > bci").end().startBlock().statement("continue").end();
            b.startIf().string("localHandlers[idx + 1] <= bci").end().startBlock().statement("continue").end();
            b.statement("bci = localHandlers[idx + 2]");
            b.statement("int handlerSp = localHandlers[idx + 3] + $root.numLocals");
            b.statement("assert sp >= handlerSp");
            b.startWhile().string("sp > handlerSp").end().startBlock();
            b.statement(clearFrame("frame", "--sp"));
            b.end();

            b.statement(setFrameObject(localFrame(), "localHandlers[idx + 4]", "ex"));

            b.statement("continue loop");

            b.end(); // for

            /**
             * NB: Reporting here ensures loop counts are reported before a guest-language exception
             * bubbles up. Loop counts may be lost when host exceptions are thrown (a compromise to
             * avoid complicating the generated code too much).
             */
            emitBeforeReturnProfiling(b);
            b.statement("throw ex");

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

            CodeTree readSlot = readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.INTEGER));
            boolean generic = ElementUtils.typeEquals(type(Object.class), slotType);

            if (!generic) {
                b.startTryBlock();
            }

            b.startStatement();
            startSetFrame(b, inputType).string(needsStackFrame ? "stackFrame" : "frame").string("sp");
            if (generic) {
                startRequireFrame(b, slotType).string("frame").tree(readSlot).end();
            } else {
                startExpectFrameUnsafe(b, "frame", slotType).tree(readSlot).end();
            }
            b.end();
            b.end(); // statement

            if (!generic) {
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
            b.declaration(type(int.class), "slot", readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.INTEGER)));
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

            b.startTryBlock();

            b.startSwitch().string("frame.getFrameDescriptor().getSlotKind(slot)").end().startBlock();
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
            startSetFrame(b, type(Object.class)).string(stackFrame).string("sp").string("value").end();
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
                            Set.of(PRIVATE, STATIC),
                            type(void.class), instructionMethodName(instr),
                            new CodeVariableElement(types.Frame, "frame"),
                            new CodeVariableElement(type(short[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(int.class), "sp"));

            boolean needsStackFrame = instr.kind == InstructionKind.STORE_LOCAL_MATERIALIZED || model.enableYield;
            if (needsStackFrame) {
                method.getParameters().add(0, new CodeVariableElement(types.Frame, "stackFrame"));
            }

            if (model.specializationDebugListener) {
                method.getParameters().add(0, new CodeVariableElement(abstractBytecodeNode.asType(), "$this"));
            }

            final TypeMirror inputType = instr.signature.getSpecializedType(0);
            final TypeMirror slotType = instr.specializedType != null ? instr.specializedType : type(Object.class);

            CodeTreeBuilder b = method.createBuilder();

            b.declaration(inputType, "local");
            b.startTryBlock();

            b.startStatement().string("local = ");
            String stackFrame = needsStackFrame ? "stackFrame" : "frame";
            startExpectFrameUnsafe(b, stackFrame, inputType).string("sp - 1").end();
            b.end();

            b.end().startCatchBlock(types.UnexpectedResultException, "ex");

            b.startStatement().startCall(lookupDoSpecializeStoreLocal(instr.getQuickeningRoot()).getSimpleName().toString());
            if (model.specializationDebugListener) {
                b.string("$this");
            }
            if (needsStackFrame) {
                b.string("stackFrame");
            }
            b.string("frame").string("bc").string("bci").string("sp").string("ex.getResult()");
            b.end().end();

            b.returnDefault();
            b.end(); // catch block

            boolean generic = ElementUtils.typeEquals(type(Object.class), inputType);

            CodeTree readSlot = readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.INTEGER));
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
                boolean needsCast = ElementUtils.needsCastTo(inputType, slotType);
                b.declaration(type(int.class), "slot", readSlot);
                b.declaration(types.FrameSlotKind, "kind", "frame.getFrameDescriptor().getSlotKind(slot)");
                b.startIf().string("kind == ").staticReference(types.FrameSlotKind, ElementUtils.firstLetterUpperCase(ElementUtils.getSimpleName(slotType)));
                b.string("|| kind == ").staticReference(types.FrameSlotKind, "Illegal");
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
                if (model.specializationDebugListener) {
                    b.string("$this");
                }
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
                            Set.of(PRIVATE, STATIC),
                            type(void.class), instructionMethodName(instr),
                            new CodeVariableElement(types.Frame, "frame"),
                            new CodeVariableElement(type(short[].class), "bc"),
                            new CodeVariableElement(type(int.class), "bci"),
                            new CodeVariableElement(type(int.class), "sp"),
                            new CodeVariableElement(type(Object.class), "local"));

            boolean needsStackFrame = instr.kind == InstructionKind.STORE_LOCAL_MATERIALIZED || model.enableYield;
            if (needsStackFrame) {
                method.getParameters().add(0, new CodeVariableElement(types.Frame, "stackFrame"));
            }

            String stackFrame = needsStackFrame ? "stackFrame" : "frame";

            if (model.specializationDebugListener) {
                method.getParameters().add(0, new CodeVariableElement(abstractBytecodeNode.asType(), "$this"));
            }

            CodeTreeBuilder b = method.createBuilder();

            b.declaration(type(short.class), "newInstruction");
            b.declaration(type(short.class), "newOperand");
            b.declaration(type(int.class), "operandIndex", readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.BYTECODE_INDEX)));
            b.declaration(type(short.class), "operand", "ACCESS.shortArrayRead(bc, operandIndex)");
            b.declaration(type(int.class), "slot", readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.INTEGER)));
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
                b.startSwitch().string("frame.getFrameDescriptor().getSlotKind(slot)").end().startBlock();

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
                emitOnSpecialize(b, "$this", "bci", "bc[bci]", "StoreLocal$" + kindName);
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
                emitOnSpecialize(b, "$this", "bci", "bc[bci]", "StoreLocal$" + genericInstruction.getQualifiedQuickeningName());
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
            emitOnSpecialize(b, "$this", "bci", "bc[bci]", "StoreLocal$" + genericInstruction.getQualifiedQuickeningName());
            b.startStatement();
            startSetFrame(b, type(Object.class)).string("frame").string("slot").string("local").end();
            b.end();

            b.end();

            b.statement("frame.getFrameDescriptor().setSlotKind(slot, newKind)");

            emitQuickeningOperand(b, "$this", "bc", "bci", null, 0, "operandIndex", "operand", "newOperand");
            emitQuickening(b, "$this", "bc", "bci", null, "newInstruction");

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
            List<CodeVariableElement> extraParams = new ArrayList<>();
            extraParams.addAll(List.of(
                            new CodeVariableElement(context.getType(short[].class), "bc"),
                            new CodeVariableElement(context.getType(int.class), "bci"),
                            new CodeVariableElement(context.getType(int.class), "sp")));

            if (!tier.isUncached()) {
                helper.getParameters().add(new CodeVariableElement(new ArrayCodeTypeMirror(types.Node), "cachedNodes"));
            }
            helper.getParameters().addAll(extraParams);

            TypeMirror cachedType = new GeneratedTypeMirror("", cachedDataClassName(instr));
            boolean isVoid = instr.signature.isVoid;

            CodeTreeBuilder b = helper.createBuilder();

            // Since an instruction produces at most one value, stackEffect is at most 1.
            int stackEffect = (isVoid ? 0 : 1) - instr.signature.valueCount;

            if (customInstructionMayReadBci(instr)) {
                storeBciInFrameIfNecessary(b);
            }

            if (!tier.isUncached()) {
                // If not in the uncached interpreter, we need to retrieve the node for the call.
                CodeTree nodeIndex = readImmediate("bc", "bci", instr.getImmediate(ImmediateKind.NODE_PROFILE));
                CodeTree readNode = CodeTreeBuilder.createBuilder().string(readNodeProfile(cachedType, nodeIndex)).build();
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

            // The tier 0 version takes all of its parameters. Other versions compute them.
            if (tier.isUncached()) {
                for (int i = 0; i < instr.signature.valueCount; i++) {
                    TypeMirror targetType = instr.signature.getGenericType(i);
                    b.startGroup();
                    if (!ElementUtils.isObject(targetType)) {
                        b.cast(targetType);
                    }
                    b.string(getFrameObject("sp - " + (instr.signature.valueCount - i)));
                    b.end();
                }

                for (InstructionImmediate immediate : instr.getImmediates(ImmediateKind.LOCAL_SETTER)) {
                    b.startStaticCall(types.LocalSetter, "get");
                    b.tree(readImmediate("bc", "bci", immediate));
                    b.end();
                }

                for (InstructionImmediate immediate : instr.getImmediates(ImmediateKind.LOCAL_SETTER_RANGE_START)) {
                    b.startStaticCall(types.LocalSetterRange, "get");
                    b.tree(readImmediate("bc", "bci", immediate)); // start
                    b.tree(readImmediate("bc", "bci", immediate)); // length
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
            b.startReturn().string("((sp - 1) << 16) | 0xffff").end();
        }

        private void emitBeforeReturnProfiling(CodeTreeBuilder b) {
            if (tier.isUncached()) {
                b.startIf().string("--uncachedExecuteCount <= 0").end().startBlock();
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
            CodeTreeBuilder b = ex.getBuilder();
            b.startReturn().startCall("continueAt");
            b.string("getRoot()");
            b.string("osrFrame");
            if (model.enableYield) {
                b.string("osrFrame");
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

    class BytecodeLocalImplFactory {
        private CodeTypeElement create() {
            bytecodeLocalImpl.setSuperClass(generic(types.BytecodeLocal, model.templateType.asType()));
            bytecodeLocalImpl.setEnclosingElement(bytecodeNodeGen);

            bytecodeLocalImpl.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), context.getType(int.class), "index"));

            bytecodeLocalImpl.add(createConstructorUsingFields(Set.of(), bytecodeLocalImpl, null));

            return bytecodeLocalImpl;
        }
    }

    class BytecodeLabelImplFactory {
        private CodeTypeElement create() {
            bytecodeLabelImpl.setSuperClass(generic(types.BytecodeLabel, model.templateType.asType()));
            bytecodeLabelImpl.setEnclosingElement(bytecodeNodeGen);

            bytecodeLabelImpl.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), context.getType(int.class), "id"));
            bytecodeLabelImpl.add(new CodeVariableElement(context.getType(int.class), "bci"));
            bytecodeLabelImpl.add(new CodeVariableElement(context.getType(int.class), "declaringOp"));
            bytecodeLabelImpl.add(new CodeVariableElement(context.getType(int.class), "finallyTryOp"));

            bytecodeLabelImpl.add(createConstructorUsingFields(Set.of(), bytecodeLabelImpl, null));
            bytecodeLabelImpl.add(createIsDefined());
            bytecodeLabelImpl.add(createEquals());
            bytecodeLabelImpl.add(createHashCode());

            return bytecodeLabelImpl;
        }

        private CodeExecutableElement createIsDefined() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), context.getType(boolean.class), "isDefined");
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn().string("bci != ").staticReference(bytecodeBuilderType, BuilderFactory.UNINIT).end();
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
            continuationRootNodeImpl.setSuperClass(types.RootNode);
            continuationRootNodeImpl.getImplements().add(types.ContinuationRootNode);

            continuationRootNodeImpl.add(new CodeVariableElement(Set.of(FINAL), bytecodeNodeGen.asType(), "root"));
            continuationRootNodeImpl.add(new CodeVariableElement(Set.of(FINAL), context.getType(int.class), "target"));
            continuationRootNodeImpl.add(GeneratorUtils.createConstructorUsingFields(
                            Set.of(), continuationRootNodeImpl,
                            ElementFilter.constructorsIn(((TypeElement) types.RootNode.asElement()).getEnclosedElements()).stream().filter(x -> x.getParameters().size() == 2).findFirst().get()));

            continuationRootNodeImpl.add(createExecute());
            continuationRootNodeImpl.add(createGetSourceRootNode());
            continuationRootNodeImpl.add(createGetLocals());

            continuationRootNodeImpl.add(createToString());

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

            b.declaration("int", "sp", "((target >> 16) & 0xffff) + root.numLocals");
            b.lineComment("Copy any existing stack values (from numLocals to sp - 1) to the current frame, which will be used for stack accesses.");
            b.statement(copyFrameTo("parentFrame", "root.numLocals", "frame", "root.numLocals", "sp - 1 - root.numLocals"));
            b.statement(setFrameObject(COROUTINE_FRAME_IDX, "parentFrame"));
            b.statement(setFrameObject("sp - 1", "inputValue"));

            b.statement("return root.continueAt(frame, parentFrame, (sp << 16) | (target & 0xffff))");

            return ex;
        }

        private CodeExecutableElement createGetSourceRootNode() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.ContinuationRootNode, "getSourceRootNode");
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn().string("root").end();
            return ex;
        }

        private CodeExecutableElement createGetLocals() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.ContinuationRootNode, "getLocals");
            CodeTreeBuilder b = ex.createBuilder();
            b.startDeclaration(types.Frame, "localFrame");
            b.cast(types.Frame);
            startGetFrame(b, "frame", type(Object.class), false).string(COROUTINE_FRAME_IDX).end();
            b.end(); // declaration
            b.startReturn().startCall("root", "getLocals").string("localFrame").end(2);
            return ex;
        }

        private CodeExecutableElement createToString() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(context.getDeclaredType(String.class), "toString");
            CodeTreeBuilder b = ex.createBuilder();

            b.startReturn().string("root.toString() + \"@\" + (target & 0xffff) ").end();
            return ex;
        }
    }

    class ContinuationLocationImplFactory {
        private CodeTypeElement create() {

            continuationLocationImpl.setEnclosingElement(bytecodeNodeGen);
            continuationLocationImpl.setSuperClass(types.ContinuationLocation);

            continuationLocationImpl.add(new CodeVariableElement(Set.of(FINAL), context.getType(int.class), "entry"));
            continuationLocationImpl.add(new CodeVariableElement(Set.of(FINAL), context.getType(int.class), "bci"));
            continuationLocationImpl.add(new CodeVariableElement(Set.of(FINAL), context.getType(int.class), "sp"));

            CodeExecutableElement ctor = createConstructorUsingFields(Set.of(), continuationLocationImpl, null);
            CodeTreeBuilder b = ctor.appendBuilder();
            b.statement("validateArgument(bci)");
            b.statement("validateArgument(sp)");

            continuationLocationImpl.add(ctor);

            continuationLocationImpl.add(compFinal(new CodeVariableElement(types.RootNode, "rootNode")));

            continuationLocationImpl.add(createValidateArgument());
            continuationLocationImpl.add(createGetRootNode());
            continuationLocationImpl.add(createToString());
            continuationLocationImpl.add(createHashCode());
            continuationLocationImpl.add(createEquals());

            return continuationLocationImpl;
        }

        private CodeExecutableElement createValidateArgument() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), context.getType(void.class), "validateArgument");
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "value"));
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("assert value >= 0");
            b.startIf().string("(1 << 16) <= value").end().startBlock();
            emitThrowIllegalStateException(b, "\"ContinuationLocation field exceeded maximum size that could be encoded.\"");
            b.end();

            return ex;
        }

        private CodeExecutableElement createGetRootNode() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.ContinuationLocation, "getRootNode");
            CodeTreeBuilder b = ex.createBuilder();

            b.startReturn().string("rootNode").end();

            return ex;
        }

        private CodeExecutableElement createToString() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(context.getDeclaredType(Object.class), "toString");
            CodeTreeBuilder b = ex.createBuilder();

            b.startReturn().string("String.format(\"ContinuationLocation [index=%d, sp=%d, bci=%04x]\", entry, sp, bci)").end();

            return ex;
        }

        private CodeExecutableElement createHashCode() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(context.getDeclaredType(Object.class), "hashCode");
            ex.getModifiers().remove(Modifier.NATIVE);
            ex.getAnnotationMirrors().clear(); // @IntrinsicCandidate
            CodeTreeBuilder b = ex.createBuilder();

            b.startReturn().startStaticCall(context.getDeclaredType(Objects.class), "hash");
            b.string("entry");
            b.string("bci");
            b.string("sp");
            b.end(2);

            return ex;
        }

        private CodeExecutableElement createEquals() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(context.getDeclaredType(Object.class), "equals");
            CodeTreeBuilder b = ex.createBuilder();

            b.startReturn().string("obj instanceof ").type(continuationLocationImpl.asType()).string(" other && this.entry == other.entry && this.bci == other.bci && this.sp == other.sp").end();

            return ex;
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

    class BoxableInterfaceFactory {
        private CodeTypeElement create() {
            boxableInterface.add(createSetBoxing());

            return boxableInterface;
        }

        private CodeExecutableElement createSetBoxing() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC, ABSTRACT), context.getType(void.class), "setBoxing");
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "index"));
            ex.addParameter(new CodeVariableElement(context.getType(byte.class), "kind"));
            return ex;
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

    private static void addDoc(CodeElement<?> element, boolean javadoc, String contents) {
        addDoc(element, javadoc, Arrays.asList(contents.split("\n")));
    }

    private static void addDoc(CodeElement<?> element, boolean javadoc, List<String> lines) {
        CodeTreeBuilder b = element.createDocBuilder();
        if (javadoc) {
            b.startJavadoc();
        } else {
            b.startDoc();
        }

        for (String line : lines) {
            if (line.length() == 0) {
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
        return String.format("ACCESS.shortArrayRead(bc, %s)", index);
    }

    private static String readConst(String index) {
        return String.format("ACCESS.objectArrayRead(constants, %s)", index);
    }

    private static String readBranchProfile(String index) {
        return String.format("branchProfiles == null ? 0 : ACCESS.intArrayRead(branchProfiles, %s)", index);
    }

    private static String readNodeProfile(TypeMirror expectedType, CodeTree index) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.startCall("ACCESS.cast");
        b.startCall("ACCESS.objectArrayRead");
        b.string("cachedNodes");
        b.tree(index);
        b.end();
        b.typeLiteral(expectedType);
        b.end();
        return b.toString();
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

    static CodeTreeBuilder startSetFrame(CodeTreeBuilder b, TypeMirror type) {
        String methodName;
        if (type == null) {
            methodName = "setValue";
        } else {
            switch (type.getKind()) {
                case BOOLEAN:
                    methodName = "setBoolean";
                    break;
                case BYTE:
                    methodName = "setByte";
                    break;
                case INT:
                    methodName = "setInt";
                    break;
                case LONG:
                    methodName = "setLong";
                    break;
                case FLOAT:
                    methodName = "setFloat";
                    break;
                case DOUBLE:
                    methodName = "setDouble";
                    break;
                default:
                    methodName = "setObject";
                    break;
            }
        }
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
        b.string(bci).string(" + ").string(immediate.offset());
        b.startComment().string(" imm ", immediate.name(), " ").end();
        b.end();
        b.end();
        return b.build();
    }

    /**
     * User code directly references some generated types and methods, like builder methods. When
     * there is an error in the model, this factory generates stubs for the user-accessible names to
     * prevent the compiler for emitting many unhelpful error messages about unknown types/methods.
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

        private void emitThrowNotImplemented(CodeTreeBuilder b) {
            b.startThrow().startNew(context.getType(AbstractMethodError.class));
            b.string("\"There are error(s) with the operation node specification. Please resolve the error(s) and recompile.\"");
            b.end(2);
        }

        private final class BuilderFactory {
            private CodeTypeElement create() {
                builder.setSuperClass(types.BytecodeBuilder);
                builder.setEnclosingElement(bytecodeNodeGen);
                mergeSuppressWarnings(builder, "all");

                builder.add(createMethodStub(new CodeExecutableElement(Set.of(PUBLIC), types.BytecodeLocal, "createLocal")));
                builder.add(createMethodStub(new CodeExecutableElement(Set.of(PUBLIC), types.BytecodeLabel, "createLabel")));

                for (OperationModel operation : model.getOperations()) {
                    if (operation.hasChildren()) {
                        builder.add(createBegin(operation));
                        builder.add(createEnd(operation));
                    } else {
                        builder.add(createEmit(operation));
                    }
                }

                return builder;
            }

            private CodeExecutableElement createMethodStub(CodeExecutableElement method) {
                emitThrowNotImplemented(method.createBuilder());
                return method;
            }

            private CodeExecutableElement createBegin(OperationModel operation) {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), context.getType(void.class), "begin" + operation.name);
                for (OperationArgument arg : operation.operationArguments) {
                    ex.addParameter(arg.toVariableElement());
                }
                return createMethodStub(ex);
            }

            private CodeExecutableElement createEnd(OperationModel operation) {
                return createMethodStub(new CodeExecutableElement(Set.of(PUBLIC),
                                operation.kind == OperationKind.ROOT ? model.templateType.asType() : context.getType(void.class),
                                "end" + operation.name));
            }

            private CodeExecutableElement createEmit(OperationModel operation) {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), context.getType(void.class), "emit" + operation.name);
                for (OperationArgument arg : operation.operationArguments) {
                    ex.addParameter(arg.toVariableElement());
                }
                return createMethodStub(ex);
            }
        }
    }
}
