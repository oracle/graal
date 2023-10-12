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
package com.oracle.truffle.dsl.processor.operations.generator;

import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.addOverride;
import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.createConstructorUsingFields;
import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.createNeverPartOfCompilation;
import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.mergeSuppressWarnings;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.firstLetterUpperCase;
import static com.oracle.truffle.dsl.processor.operations.generator.ElementHelpers.addField;
import static com.oracle.truffle.dsl.processor.operations.generator.ElementHelpers.arrayOf;
import static com.oracle.truffle.dsl.processor.operations.generator.ElementHelpers.createInitializedVariable;
import static com.oracle.truffle.dsl.processor.operations.generator.ElementHelpers.generic;
import static com.oracle.truffle.dsl.processor.operations.generator.ElementHelpers.type;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.GeneratorMode;
import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.generator.NodeConstants;
import com.oracle.truffle.dsl.processor.generator.StaticConstants;
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
import com.oracle.truffle.dsl.processor.operations.model.InstructionModel;
import com.oracle.truffle.dsl.processor.operations.model.InstructionModel.ImmediateKind;
import com.oracle.truffle.dsl.processor.operations.model.InstructionModel.InstructionImmediate;
import com.oracle.truffle.dsl.processor.operations.model.InstructionModel.InstructionKind;
import com.oracle.truffle.dsl.processor.operations.model.OperationModel;
import com.oracle.truffle.dsl.processor.operations.model.OperationModel.OperationKind;

import com.oracle.truffle.dsl.processor.operations.model.OperationsModel;
import com.oracle.truffle.dsl.processor.operations.model.ShortCircuitInstructionModel;

public class OperationsNodeFactory implements ElementHelpers {
    private static final Name Uncached_Name = CodeNames.of("Uncached");

    public static final String USER_LOCALS_START_IDX = "USER_LOCALS_START_IDX";
    public static final String BCI_IDX = "BCI_IDX";
    public static final String COROUTINE_FRAME_IDX = "COROUTINE_FRAME_IDX";

    private final ProcessorContext context = ProcessorContext.getInstance();
    private final TruffleTypes types = context.getTypes();
    private final OperationsModel model;

    // All of the following CodeTypeElements represent classes generated by this factory. Some are
    // conditionally generated depending on the model, so they get initialized in the constructor.
    // After initialization, the code for each class must still be generated; this is done by the
    // XYZFactory classes.

    // The top-level class that subclasses the node annotated with @GenerateBytecode.
    // All of the definitions that follow are nested inside of this class.
    private final CodeTypeElement operationNodeGen;

    // The builder class invoked by the language parser to generate the bytecode.
    private final CodeTypeElement builder = new CodeTypeElement(Set.of(PUBLIC, STATIC, FINAL), ElementKind.CLASS, null, "Builder");
    private final DeclaredType operationBuilderType = new GeneratedTypeMirror("", builder.getSimpleName().toString(), builder.asType());
    private final TypeMirror parserType = generic(types.BytecodeParser, operationBuilderType);

    // Implementations of public classes that Truffle interpreters interact with.
    private final CodeTypeElement bytecodeNodesImpl = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "BytecodeNodesImpl");
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

    // Represents the index that user locals start from. Depends on the number of reserved slots.
    private int userLocalsStartIndex;

    public OperationsNodeFactory(OperationsModel model) {
        this.model = model;
        operationNodeGen = GeneratorUtils.createClass(model.templateType, null, Set.of(PUBLIC, FINAL), model.getName(), model.templateType.asType());
        emptyObjectArray = addField(operationNodeGen, Set.of(PRIVATE, STATIC, FINAL), Object[].class, "EMPTY_ARRAY", "new Object[0]");
        fastAccess = addField(operationNodeGen, Set.of(PRIVATE, STATIC, FINAL), types.FastAccess, "ACCESS");
        fastAccess.setInit(createFastAccessFieldInitializer());

        if (model.enableYield) {
            continuationRootNodeImpl = new CodeTypeElement(Set.of(PUBLIC, STATIC, FINAL), ElementKind.CLASS, null, "ContinuationRootNodeImpl");
            continuationLocationImpl = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "ContinuationLocationImpl");
        } else {
            continuationRootNodeImpl = null;
            continuationLocationImpl = null;
        }
    }

    public CodeTypeElement create() {
        // Print a summary of the model in a docstring at the start.
        operationNodeGen.createDocBuilder().startDoc().lines(model.pp()).end();

        // Define constants for accessing the frame.
        operationNodeGen.addAll(createFrameLayoutConstants());

        // Define the interpreter implementations.
        if (model.enableUncachedInterpreter) {
            operationNodeGen.add(new ContinueAtFactory(InterpreterTier.UNCACHED).create());
            operationNodeGen.add(createUncachedInterpreterThreshold());
        }
        operationNodeGen.addAll(createInterpreterTiers());
        operationNodeGen.add(createCurrentTierField());
        operationNodeGen.add(new ContinueAtFactory(InterpreterTier.CACHED).create());
        operationNodeGen.add(new ContinueAtFactory(InterpreterTier.INSTRUMENTED).create());

        // Define the builder class.
        operationNodeGen.add(new BuilderFactory().create());

        // Define implementations for the public classes that Truffle interpreters interact with.
        operationNodeGen.add(new BytecodeNodesImplFactory().create());
        operationNodeGen.add(new BytecodeLocalImplFactory().create());
        operationNodeGen.add(new BytecodeLabelImplFactory().create());

        // Define helper classes containing the constants for instructions and operations.
        operationNodeGen.add(new InstructionConstantsFactory().create());
        operationNodeGen.add(new OperationsConstantsFactory().create());

        // Define the classes that model instruction data (e.g., cache data, continuation data).
        operationNodeGen.add(new BoxableInterfaceFactory().create());

        // Define the classes that implement continuations (yield).
        if (model.enableYield) {
            operationNodeGen.add(new ContinuationRootNodeImplFactory().create());
            operationNodeGen.add(new ContinuationLocationImplFactory().create());
        }

        // Define the generated node's constructor and method to set interpreter state.
        operationNodeGen.add(createConstructor());
        operationNodeGen.add(createSetInterpreterState());

        // Define the execute method.
        operationNodeGen.add(createExecute());

        // Define a continueAt method.
        // This method delegates to the current tier's continueAt, handling the case where
        // the tier changes.
        operationNodeGen.add(createContinueAt());

        // Define the members required to support OSR.
        operationNodeGen.addAll(new OSRMembersFactory().create());

        // Define a loop counter class to track how many back-edges have been taken.
        operationNodeGen.add(createLoopCounter());

        // Define the static method to create a root node.
        operationNodeGen.add(createCreate());

        // Define serialization methods and helper fields.
        if (model.enableSerialization) {
            operationNodeGen.add(createSerialize());
            operationNodeGen.add(createDeserialize());

            // Our serialized representation encodes Tags as shorts.
            // Construct mappings to/from these shorts for serialization/deserialization.
            if (!model.getProvidedTags().isEmpty()) {
                CodeExecutableElement initializeClassToTagIndex = operationNodeGen.add(createInitializeClassToTagIndex());
                CodeVariableElement classToTag = compFinal(1,
                                new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), arrayOf(generic(context.getDeclaredType(Class.class), types.Tag)), "TAG_INDEX_TO_CLASS"));
                classToTag.createInitBuilder().startStaticCall(initializeClassToTagIndex).end();
                operationNodeGen.add(classToTag);

                CodeExecutableElement initializeTagIndexToClass = operationNodeGen.add(createInitializeTagIndexToClass());
                CodeVariableElement tagToClass = new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), generic(context.getDeclaredType(ClassValue.class), context.getType(Short.class)),
                                "CLASS_TO_TAG_INDEX");
                tagToClass.createInitBuilder().startStaticCall(initializeTagIndexToClass).end();
                operationNodeGen.add(tagToClass);
            }
        }

        // Generate a {@link @TracingConfiguration} annotation, if tracing is enabled.
        if (model.enableTracing) {
            operationNodeGen.addAnnotationMirror(createTracingMetadata());
        }

        // Define the method to change between interpreters.
        operationNodeGen.add(createChangeInterpreters());

        // Define a helper method for throwing exceptions silently.
        operationNodeGen.add(createSneakyThrow());

        // Define methods for introspecting the bytecode and source.
        operationNodeGen.add(createGetIntrospectionData());
        operationNodeGen.add(createGetSourceSection());
        operationNodeGen.add(createGetSourceSectionAtBci());

        // Define methods for cloning the root node.
        operationNodeGen.add(createCloneUninitializedSupported());
        operationNodeGen.add(createCloneUninitialized());

        // Define internal state of the root node.
        operationNodeGen.add(compFinal(new CodeVariableElement(Set.of(PRIVATE), bytecodeNodesImpl.asType(), "nodes")));
        operationNodeGen.add(compFinal(1, new CodeVariableElement(Set.of(PRIVATE), context.getType(short[].class), "bc")));
        operationNodeGen.add(compFinal(1, new CodeVariableElement(Set.of(PRIVATE), context.getType(Object[].class), "constants")));
        operationNodeGen.add(compFinal(1, new CodeVariableElement(Set.of(PRIVATE), arrayOf(types.Node), "cachedNodes")));
        operationNodeGen.add(compFinal(1, new CodeVariableElement(Set.of(PRIVATE), arrayOf(context.getType(int.class)), "branchProfiles")));
        operationNodeGen.add(compFinal(1, new CodeVariableElement(Set.of(PRIVATE), context.getType(int[].class), "handlers")));
        operationNodeGen.add(compFinal(1, new CodeVariableElement(Set.of(PRIVATE, VOLATILE), context.getType(int[].class), "sourceInfo")));
        operationNodeGen.add(compFinal(new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "numLocals")));
        operationNodeGen.add(compFinal(new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "numNodes")));
        operationNodeGen.add(compFinal(new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "numConditionalBranches")));
        operationNodeGen.add(compFinal(new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "buildIndex")));
        if (model.enableUncachedInterpreter) {
            operationNodeGen.add(new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "uncachedExecuteCount")).createInitBuilder().string("16");
        }
        if (model.enableTracing) {
            operationNodeGen.add(compFinal(1, new CodeVariableElement(Set.of(PRIVATE), context.getType(boolean[].class), "basicBlockBoundary")));
        }

        // Define helpers for variadic accesses.
        operationNodeGen.add(createReadVariadic());
        operationNodeGen.add(createMergeVariadic());

        // Define helpers for local reading/copying.
        operationNodeGen.add(createGetLocals());
        operationNodeGen.addAll(createCopyLocals());

        // Define helpers for bci lookups.
        operationNodeGen.add(createFindBciOfOperationNode());
        operationNodeGen.add(createReadBciFromFrame());

        // Define the generated Node classes for custom instructions.
        StaticConstants consts = new StaticConstants();
        for (InstructionModel instr : model.getInstructions()) {
            if (instr.nodeData == null) {
                continue;
            }

            NodeConstants nodeConsts = new NodeConstants();
            OperationNodeGeneratorPlugs plugs = new OperationNodeGeneratorPlugs(context, operationNodeGen.asType(), model, instr);
            FlatNodeGenFactory factory = new FlatNodeGenFactory(context, GeneratorMode.DEFAULT, instr.nodeData, consts, nodeConsts, plugs);

            CodeTypeElement el = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, cachedDataClassName(instr));
            mergeSuppressWarnings(el, "static-method");
            el.setSuperClass(types.Node);
            factory.create(el);
            new CustomInstructionPostProcessor().process(el);

            nodeConsts.prependToClass(el);
            operationNodeGen.add(el);
        }
        consts.addElementsTo(operationNodeGen);

        // Define helpers for obtaining and initializing the cached nodes.
        operationNodeGen.add(createGetCachedNodes());
        operationNodeGen.add(createInitializeCachedNodes());
        operationNodeGen.add(createCreateCachedNodes());

        // Define helpers for obtaining and initializing branch profiles.
        operationNodeGen.add(createGetBranchProfiles());
        operationNodeGen.add(createInitializeBranchProfiles());

        return operationNodeGen;
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
        b.staticReference(types.FastAccess, model.allowUnsafe ? "UNSAFE" : "SAFE");
        return b.build();
    }

    private CodeExecutableElement createGetSourceSection() {
        CodeExecutableElement ex = GeneratorUtils.override(types.Node, "getSourceSection");
        CodeTreeBuilder b = ex.createBuilder();

        b.statement("nodes.ensureSources()");
        b.startIf().string("sourceInfo.length == 0").end().startBlock();
        b.returnNull();
        b.end();

        b.statement("Source[] sources = nodes.getSources()");

        b.startIf().string("sources == null").end().startBlock();
        b.returnNull();
        b.end();

        b.startReturn().string("sources[(sourceInfo[0] >> 16) & 0xffff].createSection(sourceInfo[1], sourceInfo[2])").end();

        return ex;
    }

    private CodeExecutableElement createGetSourceSectionAtBci() {
        CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeRootNode, "getSourceSectionAtBci");
        ex.renameArguments("bci");
        CodeTreeBuilder b = ex.createBuilder();

        b.statement("nodes.ensureSources()");
        b.startIf().string("sourceInfo.length == 0").end().startBlock();
        b.returnNull();
        b.end();

        b.statement("Source[] sources = nodes.getSources()");

        b.startIf().string("sources == null").end().startBlock();
        b.returnNull();
        b.end();

        b.statement("int i = 0");

        b.startWhile().string("i < sourceInfo.length && (sourceInfo[i] & 0xffff) <= bci").end().startBlock();
        b.statement("i += 3");
        b.end();

        b.startIf().string("i == 0").end().startBlock();
        b.returnNull();
        b.end();

        b.statement("i -= 3");

        b.startIf().string("sourceInfo[i + 1] == -1").end().startBlock();
        b.returnNull();
        b.end();

        b.statement("return sources[(sourceInfo[i] >> 16) & 0xffff].createSection(sourceInfo[i + 1], sourceInfo[i + 2])");

        return ex;
    }

    private CodeExecutableElement createCloneUninitializedSupported() {
        CodeExecutableElement ex = GeneratorUtils.override(types.RootNode, "isCloneUninitializedSupported");
        ex.createBuilder().returnTrue();
        return ex;
    }

    private CodeExecutableElement createCloneUninitialized() {
        CodeExecutableElement ex = GeneratorUtils.override(types.RootNode, "cloneUninitialized");

        CodeTreeBuilder b = ex.createBuilder();

        b.declaration(operationNodeGen.asType(), "clone", cast(operationNodeGen.asType(), "this.copy()"));

        // The base copy method performs a shallow copy of all fields.
        // Some fields should be manually reinitialized to default values.
        b.statement("clone.cachedNodes = null"); // cachedNodes will be set on first execution

        if (model.enableUncachedInterpreter) {
            b.statement("clone.uncachedExecuteCount = 16");
            b.statement("clone.currentTier = " + InterpreterTier.UNCACHED.name());
        } else {
            b.statement("clone.currentTier = " + InterpreterTier.CACHED.name());
        }

        b.startReturn().string("clone").end();

        return ex;
    }

    private CodeExecutableElement createUncachedInterpreterThreshold() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeRootNode, "setUncachedInterpreterThreshold");

        CodeTreeBuilder b = ex.createBuilder();
        b.startAssign("uncachedExecuteCount").string("invocationCount").end();
        b.startIf().string("invocationCount == 0").end().startBlock();
        b.startAssign("currentTier").string(InterpreterTier.CACHED.name()).end();
        b.end();

        return ex;
    }

    private enum InterpreterTier {
        UNCACHED("Uncached", true, false),
        CACHED("Cached", false, false),
        INSTRUMENTED("Instrumented", false, true);

        final String friendlyName;
        final boolean isUncached;
        final boolean isInstrumented;

        InterpreterTier(String friendlyName, boolean isUncached, boolean isInstrumented) {
            this.friendlyName = friendlyName;
            this.isUncached = isUncached;
            this.isInstrumented = isInstrumented;
        }

        public String interpreterClassName() {
            return friendlyName + "Interpreter";
        }
    }

    private List<InterpreterTier> getInterpreterTiers() {
        List<InterpreterTier> tiers = new ArrayList<>();
        if (model.enableUncachedInterpreter) {
            tiers.add(InterpreterTier.UNCACHED);
        }
        tiers.add(InterpreterTier.CACHED);
        tiers.add(InterpreterTier.INSTRUMENTED);
        return tiers;
    }

    private List<CodeVariableElement> createInterpreterTiers() {
        List<InterpreterTier> tiers = getInterpreterTiers();

        List<CodeVariableElement> results = new ArrayList<>();
        for (int i = 0; i < tiers.size(); i++) {
            CodeVariableElement fld = new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), context.getType(int.class), tiers.get(i).name());
            fld.createInitBuilder().string(i).end();
            results.add(fld);
        }
        return results;
    }

    private CodeVariableElement createCurrentTierField() {
        CodeVariableElement fld = new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "currentTier");
        fld = compFinal(fld);

        if (model.enableUncachedInterpreter) {
            fld.createInitBuilder().string(InterpreterTier.UNCACHED.name());
        } else {
            fld.createInitBuilder().string(InterpreterTier.CACHED.name());
        }

        return fld;
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

    private CodeExecutableElement createConstructor() {
        CodeExecutableElement ctor = new CodeExecutableElement(Set.of(PRIVATE), null, operationNodeGen.getSimpleName().toString());
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

        return ctor;
    }

    private CodeExecutableElement createSetInterpreterState() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "setInterpreterState");
        List<CodeVariableElement> params = new ArrayList<>();
        params.addAll(List.of(
                        new CodeVariableElement(bytecodeNodesImpl.asType(), "nodes"),
                        new CodeVariableElement(context.getType(short[].class), "bc"),
                        new CodeVariableElement(context.getType(Object[].class), "constants"),
                        new CodeVariableElement(context.getType(int[].class), "handlers"),
                        new CodeVariableElement(context.getType(int.class), "numLocals"),
                        new CodeVariableElement(context.getType(int.class), "numNodes"),
                        new CodeVariableElement(context.getType(int.class), "numConditionalBranches"),
                        new CodeVariableElement(context.getType(int.class), "buildIndex")));
        if (model.enableTracing) {
            params.add(new CodeVariableElement(context.getType(int[].class), "basicBlockBoundary"));
        }

        CodeTreeBuilder b = ex.getBuilder();

        for (CodeVariableElement param : params) {
            ex.addParameter(param);
            b.startAssign("this", param).variable(param).end();
        }

        return ex;
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

    private CodeExecutableElement createContinueAt() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(Object.class), "continueAt");
        mergeSuppressWarnings(ex, "hiding");
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

        b.startTryBlock();

        b.statement("int state = startState");
        // These don't change between invocations. Read them once.
        b.statement("short[] bc = this.bc");
        b.statement("Object[] constants = this.constants");

        b.startWhile().string("true").end().startBlock();

        b.startSwitch().string("currentTier").end().startBlock();

        for (InterpreterTier tier : getInterpreterTiers()) {
            b.startCase().string(tier.name()).end().startBlock();
            if (tier.isUncached) {
                // We don't want to compile this code path.
                b.tree(createTransferToInterpreterAndInvalidate("this"));
            } else {
                // Obtain the cached nodes, forcing initialization if necessary
                b.statement("Node[] cachedNodes = getCachedNodes()");
                b.statement("int[] branchProfiles = getBranchProfiles()");
            }
            b.startAssign("state").startCall(tier.interpreterClassName() + ".continueAt");
            b.string("this, frame");
            if (model.enableYield) {
                b.string("localFrame");
            }
            b.string("bc");
            b.string("constants");
            if (!tier.isUncached) {
                b.string("cachedNodes");
                b.string("branchProfiles");
            }
            b.string("state");
            b.end(2);
            b.statement("break");
            b.end();
        }
        b.end(); // switch

        b.startIf().string("(state & 0xffff) == 0xffff").end().startBlock();
        b.statement("break");
        b.end().startElseBlock();
        b.lineComment("tier changed");
        b.tree(createTransferToInterpreterAndInvalidate("this"));
        b.end();
        b.end();

        String returnValue = getFrameObject("(state >> 16) & 0xffff");
        if (model.executeEpilog != null) {
            b.startAssign("returnValue").string(returnValue).end();
            b.statement("return returnValue");
        } else {
            b.startReturn().string(returnValue).end();
        }

        b.end().startCatchBlock(context.getType(Throwable.class), "th");
        if (model.executeEpilog != null) {
            b.statement("throw sneakyThrow(throwable = th)");
            b.end().startFinallyBlock();
            b.statement("this.executeEpilog(" + localFrame + ", returnValue, throwable)");
        } else {
            b.statement("throw sneakyThrow(th)");
        }
        b.end();

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

    private CodeTypeElement createLoopCounter() {
        addField(loopCounter, Set.of(PRIVATE, STATIC, FINAL), int.class, "REPORT_LOOP_STRIDE", "1 << 8");
        addField(loopCounter, Set.of(PRIVATE), int.class, "value");

        return loopCounter;
    }

    private CodeExecutableElement createCreate() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC, STATIC), generic(types.BytecodeNodes, model.templateType.asType()), "create");
        ex.addParameter(new CodeVariableElement(types.BytecodeConfig, "config"));
        ex.addParameter(new CodeVariableElement(generic(types.BytecodeParser, builder.asType()), "generator"));

        CodeTreeBuilder b = ex.getBuilder();

        b.declaration("BytecodeNodesImpl", "nodes", "new BytecodeNodesImpl(generator)");
        b.startAssign("Builder builder").startNew(builder.getSimpleName().toString());
        b.string("nodes");
        b.string("false");
        b.string("config");
        b.end(2);

        b.startStatement().startCall("generator", "parse");
        b.string("builder");
        b.end(2);

        b.startStatement().startCall("builder", "finish").end(2);

        b.startReturn().string("nodes").end();

        return ex;
    }

    private CodeExecutableElement createInitializeClassToTagIndex() {
        ArrayType rawArray = arrayOf(context.getType(Class.class));
        ArrayType genericArray = arrayOf(generic(context.getDeclaredType(Class.class), types.Tag));
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), genericArray, "initializeClassToTagIndex");
        CodeTreeBuilder b = method.createBuilder();

        b.startReturn();
        b.cast(genericArray);
        b.startNewArray(rawArray, null);

        for (TypeMirror tagClass : model.getProvidedTags()) {
            b.typeLiteral(tagClass);
        }

        b.end();
        b.end();

        mergeSuppressWarnings(method, "unchecked");

        return method;
    }

    private CodeExecutableElement createInitializeTagIndexToClass() {
        DeclaredType classValue = context.getDeclaredType(ClassValue.class);
        TypeMirror classValueType = generic(classValue, context.getType(Short.class));

        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), classValueType,
                        "initializeTagIndexToClass");
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
            b.startReturn().string(index).end();
            b.end();
            index++;
        }
        b.startReturn().string(-1).end();

        b.end();

        b.end();
        b.end();

        return method;
    }

    private CodeExecutableElement createSerialize() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PUBLIC, STATIC), context.getType(void.class), "serialize");
        method.addParameter(new CodeVariableElement(types.BytecodeConfig, "config"));
        method.addParameter(new CodeVariableElement(context.getType(DataOutput.class), "buffer"));
        method.addParameter(new CodeVariableElement(types.BytecodeSerializer, "callback"));
        method.addParameter(new CodeVariableElement(parserType, "parser"));
        method.addThrownType(context.getType(IOException.class));

        CodeTreeBuilder init = CodeTreeBuilder.createBuilder();
        init.startNew(operationBuilderType);
        init.startGroup();
        init.cast(bytecodeNodesImpl.asType());
        init.string("create(config, parser)");
        init.end();
        init.string("false");
        init.string("config");
        init.end();

        CodeTreeBuilder b = method.createBuilder();
        b.declaration(operationBuilderType, "builder", init.build());

        b.startTryBlock();

        b.startStatement().startCall("builder", "serialize");
        b.string("buffer");
        b.string("callback");
        b.end().end();

        b.end().startCatchBlock(context.getType(IOError.class), "e");
        b.startThrow().cast(context.getType(IOException.class), "e.getCause()").end();
        b.end();

        return method;
    }

    private CodeExecutableElement createDeserialize() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PUBLIC, STATIC),
                        generic(types.BytecodeNodes, model.getTemplateType().asType()), "deserialize");

        method.addParameter(new CodeVariableElement(types.TruffleLanguage, "language"));
        method.addParameter(new CodeVariableElement(types.BytecodeConfig, "config"));
        method.addParameter(new CodeVariableElement(generic(Supplier.class, DataInput.class), "input"));
        method.addParameter(new CodeVariableElement(types.BytecodeDeserializer, "callback"));
        method.addThrownType(context.getType(IOException.class));
        CodeTreeBuilder b = method.createBuilder();

        b.startTryBlock();

        b.statement("return create(config, (b) -> b.deserialize(language, input, callback))");
        b.end().startCatchBlock(type(IOError.class), "e");
        b.startThrow().cast(type(IOException.class), "e.getCause()").end();
        b.end();

        return method;
    }

    private CodeExecutableElement createGetIntrospectionData() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), types.BytecodeIntrospection, "getIntrospectionData");
        CodeTreeBuilder b = ex.createBuilder();

        b.declaration(generic(context.getDeclaredType(List.class), context.getDeclaredType(Object.class)), "instructions", "new ArrayList<>()");

        b.startFor().string("int bci = 0; bci < bc.length;").end().startBlock();

        b.startSwitch().string(readBc("bci")).end().startBlock();

        for (InstructionModel instr : model.getInstructions()) {
            b.startCase().tree(createInstructionConstant(instr)).end().startBlock();
            b.startStatement().startCall("instructions.add").startNewArray(arrayOf(context.getType(Object.class)), null);
            b.string("bci");
            b.doubleQuote(instr.name);
            b.string("new short[] {" + instr.id + "}");

            b.startNewArray(arrayOf(context.getType(Object.class)), null);

            switch (instr.kind) {
                case BRANCH:
                case BRANCH_BACKWARD:
                    buildIntrospectionArgument(b, "BRANCH_OFFSET", readBc("bci + 1"));
                    break;
                case BRANCH_FALSE:
                    buildIntrospectionArgument(b, "BRANCH_OFFSET", readBc("bci + 1"));
                    buildIntrospectionArgument(b, "PROFILE", "new int[] {" + readProfile(readBc("bci + 2") + " * 2") + ", " + readProfile(readBc("bci + 2") + " * 2 + 1") + "}");
                    break;
                case LOAD_CONSTANT:
                    buildIntrospectionArgument(b, "CONSTANT", readConst(readBc("bci + 1")));
                    break;
                case LOAD_ARGUMENT:
                    buildIntrospectionArgument(b, "ARGUMENT", readBc("bci + 1"));
                    break;
                case LOAD_LOCAL:
                case STORE_LOCAL:
                case LOAD_LOCAL_MATERIALIZED:
                case STORE_LOCAL_MATERIALIZED:
                case THROW:
                    buildIntrospectionArgument(b, "LOCAL", readBc("bci + 1"));
                    break;
                case CUSTOM:
                    break;
                case CUSTOM_SHORT_CIRCUIT:
                    assert !instr.getImmediates().isEmpty() : "Short circuit operations should always have branch targets.";
                    buildIntrospectionArgument(b, "BRANCH_OFFSET", readBc("bci + 1"));
                    break;
            }

            b.end();

            b.end(3);
            b.statement("bci += " + instr.getInstructionLength());
            b.statement("break");
            b.end();
        }

        b.end();

        b.end();

        b.statement("Object[] exHandlersInfo = new Object[handlers.length / 5]");

        b.startFor().string("int idx = 0; idx < exHandlersInfo.length; idx++").end().startBlock();
        b.statement("exHandlersInfo[idx] = new Object[]{ handlers[idx*5], handlers[idx*5 + 1], handlers[idx*5 + 2], handlers[idx*5 + 4] }");
        b.end();

        // todo: source info

        b.startReturn().startStaticCall(types.BytecodeIntrospection_Provider, "create");
        b.string("new Object[]{0, instructions.toArray(), exHandlersInfo, null}");
        b.end(2);

        return ex;
    }

    private void buildIntrospectionArgument(CodeTreeBuilder b, String kind, String content) {
        b.startNewArray(arrayOf(context.getType(Object.class)), null);
        b.staticReference(types.Argument_ArgumentKind, kind);
        b.string(content);
        b.end();

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
        b.statement(clearFrame("index"));
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

    private CodeExecutableElement createGetLocals() {
        CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeRootNode, "getLocals");
        ex.addAnnotationMirror(createExplodeLoopAnnotation(null));

        CodeTreeBuilder b = ex.createBuilder();

        b.declaration(context.getType(Object[].class), "result", "new Object[numLocals - " + USER_LOCALS_START_IDX + "]");
        b.startFor().string("int i = 0; i < numLocals - " + USER_LOCALS_START_IDX + "; i++").end().startBlock();
        b.statement("result[i] = ACCESS.getObject(frame, i + " + USER_LOCALS_START_IDX + ")");
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
        copyLocalsBuilder.startStatement().startCall("ACCESS.copyTo");
        copyLocalsBuilder.string("source");
        copyLocalsBuilder.string("USER_LOCALS_START_IDX");
        copyLocalsBuilder.string("destination");
        copyLocalsBuilder.string("USER_LOCALS_START_IDX");
        copyLocalsBuilder.string("length");
        copyLocalsBuilder.end(2);

        return List.of(copyAllLocals, copyLocals);
    }

    private CodeExecutableElement createFindBciOfOperationNode() {
        CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeRootNode, "findBciOfOperationNode");

        CodeTreeBuilder b = ex.createBuilder();
        b.tree(createNeverPartOfCompilation());
        b.declaration(arrayOf(types.Node), "localNodes", "cachedNodes");
        b.startIf().string("localNodes == null").end().startBlock();
        b.startReturn().string("-1").end();
        b.end();

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
            // NB: this relies on all custom instructions encoding their node as the last immediate.
            InstructionModel representativeInstruction = entry.getValue().get(0);
            InstructionImmediate imm = representativeInstruction.getImmediate(ImmediateKind.NODE);
            b.startBlock();
            b.statement("nodeIndex = " + readBc("bci + " + imm.offset));
            b.statement("bci += " + representativeInstruction.getInstructionLength());
            b.statement("break");
            b.end();
        }

        b.caseDefault().startBlock();
        buildThrow(b, AssertionError.class, "\"Should not reach here\"");
        b.end();

        b.end(); // } switch

        // nodeIndex is guaranteed to be set, since we continue to the top of the loop when there's
        // no node.
        b.startIf().string("localNodes[nodeIndex] == operationNode").end().startBlock();
        b.startReturn().string("currentBci").end();
        b.end();

        b.end(); // } while

        // Fallback: the node wasn't found.
        b.startReturn().string("-1").end();

        return ex;

    }

    private CodeExecutableElement createReadBciFromFrame() {
        CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeRootNode, "readBciFromFrame");

        CodeTreeBuilder b = ex.createBuilder();
        if (model.needsBciSlot()) {
            b.startReturn().string("ACCESS.getInt(frame, " + BCI_IDX + ")").end();
        } else {
            b.lineComment("The bci is not stored in the frame.");
            b.startReturn().string("-1").end();
        }

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

    private CodeExecutableElement createChangeInterpreters() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "changeInterpreters");
        mergeSuppressWarnings(ex, "hiding");

        ex.addParameter(new CodeVariableElement(context.getType(int.class), "newTier"));

        CodeTreeBuilder b = ex.createBuilder();

        b.statement("int currentTier = this.currentTier");

        b.startIf().string("newTier == currentTier").end().startBlock();
        b.returnStatement();
        b.end();

        b.startIf().string("newTier == CACHED && currentTier == INSTRUMENTED").end().startBlock();
        b.returnStatement();
        b.end();

        b.tree(createTransferToInterpreterAndInvalidate("this"));
        b.statement("this.currentTier = newTier");

        return ex;
    }

    private CodeExecutableElement createCreateCachedNodes() {
        TypeMirror nodeArrayType = new ArrayCodeTypeMirror(types.Node);
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), nodeArrayType, "createCachedNodes");
        CodeTreeBuilder b = ex.createBuilder();

        b.tree(createNeverPartOfCompilation());
        b.declaration(nodeArrayType, "result", "new Node[numNodes]");
        b.statement("int bci = 0");
        b.string("loop: ").startWhile().string("bci < bc.length").end().startBlock();
        b.statement("int nodeIndex");
        b.statement("Node node");
        b.startSwitch().string(readBc("bci")).end().startBlock();

        Map<Boolean, List<InstructionModel>> instructionsGroupedByHasNode = model.getInstructions().stream().collect(Collectors.partitioningBy(instr -> instr.hasNodeImmediate()));
        Map<Integer, List<InstructionModel>> nodelessGroupedByLength = instructionsGroupedByHasNode.get(false).stream().collect(
                        Collectors.groupingBy(instr -> instr.getInstructionLength()));

        // Skip the instructions without nodes. Group by size to simplify the generated code.
        for (Map.Entry<Integer, List<InstructionModel>> entry : nodelessGroupedByLength.entrySet()) {
            for (InstructionModel instr : entry.getValue()) {
                b.startCase().tree(createInstructionConstant(instr)).end();
            }
            b.startBlock();
            b.statement("bci += " + entry.getKey());
            b.statement("continue loop");
            b.end();
        }

        for (InstructionModel instr : instructionsGroupedByHasNode.get(true)) {
            b.startCase().tree(createInstructionConstant(instr)).end().startBlock();
            InstructionImmediate imm = instr.getImmediate(ImmediateKind.NODE);
            b.statement("nodeIndex = " + readBc("bci + " + imm.offset));
            b.statement("node = new " + cachedDataClassName(instr) + "()");
            b.statement("bci += " + instr.getInstructionLength());
            b.statement("break");
            b.end();
        }

        b.caseDefault().startBlock();
        buildThrow(b, AssertionError.class, "\"Should not reach here\"");
        b.end();

        b.end(); // } switch

        // node and nodeIndex are guaranteed to be set, since we continue to the top of the loop
        // when there's no node.
        b.startAssert().string("result[nodeIndex] == null").end();
        b.statement("result[nodeIndex] = insert(node)");

        b.end(); // } while
        b.startAssert().string("bci == bc.length").end();

        b.startReturn().string("result").end();

        return ex;
    }

    private CodeExecutableElement createInitializeCachedNodes() {
        TypeMirror nodeArrayType = new ArrayCodeTypeMirror(types.Node);
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), nodeArrayType, "initializeCachedNodes");
        CodeTreeBuilder b = ex.createBuilder();

        b.tree(createNeverPartOfCompilation());
        b.declaration(nodeArrayType, "result", "this.cachedNodes");

        b.startIf().string("result != null").end().startBlock();
        b.startReturn().string("result").end();
        b.end();

        b.startAssign("result").startCall("createCachedNodes").end(2);

        b.lineComment("For thread-safety, ensure that all stores into \"result\" happen before we make \"cachedNodes\" point to it.");
        buildFence(b);
        b.startAssign("this.cachedNodes").string("result").end();
        b.startReturn().string("result").end();

        return ex;
    }

    private CodeExecutableElement createGetCachedNodes() {
        TypeMirror nodeArrayType = new ArrayCodeTypeMirror(types.Node);
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), nodeArrayType, "getCachedNodes");
        CodeTreeBuilder b = ex.createBuilder();

        b.declaration(nodeArrayType, "result", "this.cachedNodes");

        b.startIf().string("result == null").end().startBlock();
        b.tree(createTransferToInterpreterAndInvalidate("this"));
        b.startAssign("result").startCall("initializeCachedNodes").end(2);
        b.end();

        b.startReturn().string("result").end();
        b.end();

        return ex;
    }

    private CodeExecutableElement createGetBranchProfiles() {
        TypeMirror intArrayType = arrayOf(context.getType(int.class));
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), intArrayType, "getBranchProfiles");
        CodeTreeBuilder b = ex.createBuilder();

        b.declaration(intArrayType, "result", "this.branchProfiles");

        b.startIf().string("result == null").end().startBlock();
        b.tree(createTransferToInterpreterAndInvalidate("this"));
        b.startAssign("result").startCall("initializeBranchProfiles").end(2);
        b.end();

        b.startReturn().string("result").end();
        b.end();

        return ex;
    }

    private CodeExecutableElement createInitializeBranchProfiles() {
        TypeMirror intArrayType = arrayOf(context.getType(int.class));
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), intArrayType, "initializeBranchProfiles");
        CodeTreeBuilder b = ex.createBuilder();

        b.tree(createNeverPartOfCompilation());
        b.declaration(intArrayType, "result", "this.branchProfiles");

        b.startIf().string("result != null").end().startBlock();
        b.startReturn().string("result").end();
        b.end();

        b.startAssign("result").startStaticCall(types.BytecodeSupport, "allocateBranchProfiles").string("numConditionalBranches").end(2);
        b.startAssign("this.branchProfiles").string("result").end();
        b.startReturn().string("result").end();

        return ex;
    }

    final class SerializationStateElements implements ElementHelpers {

        final CodeTypeElement serializationState = new CodeTypeElement(Set.of(PRIVATE, STATIC), ElementKind.CLASS, null, "SerializationState");

        final CodeVariableElement codeCreateLabel = addField(serializationState, Set.of(PRIVATE, STATIC, FINAL), short.class, "CODE_$CREATE_LABEL", "-2");
        final CodeVariableElement codeCreateLocal = addField(serializationState, Set.of(PRIVATE, STATIC, FINAL), short.class, "CODE_$CREATE_LOCAL", "-3");
        final CodeVariableElement codeCreateObject = addField(serializationState, Set.of(PRIVATE, STATIC, FINAL), short.class, "CODE_$CREATE_OBJECT", "-4");
        final CodeVariableElement codeEndSerialize = addField(serializationState, Set.of(PRIVATE, STATIC, FINAL), short.class, "CODE_$END", "-5");

        final CodeVariableElement builtNodes = addField(serializationState, Set.of(PRIVATE, FINAL), generic(ArrayList.class, operationNodeGen.asType()), "builtNodes");
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
            b.cast(operationNodeGen.asType()).string("node).buildIndex)");
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
        TypeMirror localNamesType = generic(ArrayList.class, context.getDeclaredType(Object.class));

        // When we enter a FinallyTry, these fields get stored on the FinallyTryContext.
        // On exit, they are restored.
        List<CodeVariableElement> builderContextSensitiveState = new ArrayList<>(List.of(
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(short[].class), "bc"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "bci"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "curStack"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "maxStack"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int[].class), "sourceInfo"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "sourceInfoIndex"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int[].class), "exHandlers"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "exHandlerCount"),
                        new CodeVariableElement(Set.of(PRIVATE), unresolvedLabelsType, "unresolvedLabels")));

        // This state is shared across all contexts for a given root node. It does not get
        // saved/restored when entering/leaving a FinallyTry.
        List<CodeVariableElement> builderContextInsensitiveState = new ArrayList<>(List.of(
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
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "opSeqNum"),
                        new CodeVariableElement(Set.of(PRIVATE), finallyTryContext.asType(), "finallyTryContext"),
                        new CodeVariableElement(Set.of(PRIVATE), constantPool.asType(), "constantPool"),
                        new CodeVariableElement(Set.of(PRIVATE), localNamesType, "localNames"),
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
                                new CodeVariableElement(context.getType(int.class), "handlerMaxStack"),
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
                deserializerContextImpl.setEnclosingElement(operationNodeGen);
                deserializerContextImpl.getImplements().add(types.BytecodeDeserializer_DeserializerContext);

                deserializerContextImpl.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), generic(ArrayList.class, operationNodeGen.asType()), "builtNodes"));
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
            builder.setSuperClass(types.BytecodeBuilder);
            builder.setEnclosingElement(operationNodeGen);

            builder.add(uninitialized);
            uninitialized.createInitBuilder().string(-1).end();

            builder.add(new SavedStateFactory().create());
            builder.add(new OperationStackEntryFactory().create());
            builder.add(new FinallyTryContextFactory().create());
            builder.add(new ConstantPoolFactory().create());
            builder.add(new BytecodeLocationFactory().create());

            builder.add(createOperationNames());

            builder.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), bytecodeNodesImpl.asType(), "nodes"));
            builder.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), context.getType(boolean.class), "isReparse"));
            builder.add(new CodeVariableElement(Set.of(PRIVATE), context.getType(boolean.class), "withSource"));
            builder.add(new CodeVariableElement(Set.of(PRIVATE), context.getType(boolean.class), "withInstrumentation"));
            builder.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), generic(ArrayList.class, operationNodeGen.asType()), "builtNodes"));

            builder.add(new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "buildIndex"));
            builder.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), generic(ArrayList.class, types.Source), "sources"));

            if (model.enableSerialization) {
                serializationElements = new SerializationStateElements();
                builder.add(serializationElements.serializationState);
                serialization = builder.add(new CodeVariableElement(Set.of(PRIVATE),
                                serializationElements.serializationState.asType(), "serialization"));
                builder.add(new DeserializerContextImplFactory().create());
            }

            builder.add(createConstructor());

            builder.addAll(builderState);

            builder.add(createCreateLocal());
            builder.add(createCreateLocalWithName());
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
            builder.add(createDoEmitInstruction());
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

            return builder;
        }

        private CodeExecutableElement createSerialize() {
            CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE),
                            context.getType(void.class), "serialize");
            method.addParameter(new CodeVariableElement(type(DataOutput.class), "buffer"));
            method.addParameter(new CodeVariableElement(types.BytecodeSerializer, "callback"));
            method.addThrownType(context.getType(IOException.class));
            CodeTreeBuilder b = method.createBuilder();

            b.statement("this.serialization = new SerializationState(builtNodes, buffer, callback)");

            b.startTryBlock();

            b.startStatement().startCall(castParser(method, "nodes.getParser()"), "parse").string("this").end(2);

            b.statement("short[][] nodeIndices = new short[builtNodes.size()][]");
            b.startFor().string("int i = 0; i < nodeIndices.length; i ++").end().startBlock();

            b.declaration(operationNodeGen.asType(), "node", "builtNodes.get(i)");

            b.statement("short[] indices = nodeIndices[i] = new short[" + model.serializedFields.size() + "]");

            for (int i = 0; i < model.serializedFields.size(); i++) {
                VariableElement var = model.serializedFields.get(i);
                b.startStatement();
                b.string("indices[").string(i).string("] = ");
                b.startCall("serialization.serializeObject");
                b.startGroup();
                b.string("node.").string(var.getSimpleName().toString());
                b.end();
                b.end();
                b.end();
            }

            b.end(); // node for

            serializationElements.writeShort(b, serializationElements.codeEndSerialize);

            b.startFor().string("int i = 0; i < nodeIndices.length; i++").end().startBlock();
            b.statement("short[] indices = nodeIndices[i]");

            for (int i = 0; i < model.serializedFields.size(); i++) {
                serializationElements.writeShort(b, "indices[" + i + "]");
            }
            b.end();

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
            b.declaration(generic(context.getDeclaredType(ArrayList.class), operationNodeGen.asType()), "builtNodes", "new ArrayList<>()");

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

            b.startFor().string("int i = 0; i < builtNodes.size(); i++").end().startBlock();
            b.declaration(operationNodeGen.asType(), "node", "builtNodes.get(i)");

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

                for (int i = 0; i < operation.operationArgumentTypes.length; i++) {
                    TypeMirror argType = operation.operationArgumentTypes[i];
                    String argumentName = operation.getOperationArgumentName(i);
                    if (ElementUtils.typeEquals(argType, types.TruffleLanguage)) {
                        continue; // language is already available as a parameter
                    } else if (ElementUtils.typeEquals(argType, types.BytecodeLocal)) {
                        b.statement("BytecodeLocal ", argumentName, " = locals.get(buffer.readShort())");
                    } else if (ElementUtils.typeEquals(argType, new ArrayCodeTypeMirror(types.BytecodeLocal))) {
                        b.statement("BytecodeLocal[] ", argumentName, " = new BytecodeLocal[buffer.readShort()]");
                        b.startFor().string("int i = 0; i < ", argumentName, ".length; i++").end().startBlock();
                        // this can be optimized since they are consecutive
                        b.statement(argumentName, "[i] = locals.get(buffer.readShort());");
                        b.end();
                    } else if (ElementUtils.typeEquals(argType, types.BytecodeLabel)) {
                        b.statement("BytecodeLabel ", argumentName, " = labels.get(buffer.readShort())");
                    } else if (ElementUtils.typeEquals(argType, context.getType(int.class))) {
                        b.statement("int ", argumentName, " = buffer.readInt()");
                    } else if (operation.kind == OperationKind.INSTRUMENT_TAG && i == 0) {
                        b.startStatement().type(argType).string(" ", argumentName, " = TAG_INDEX_TO_CLASS[buffer.readShort()]").end();
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

                for (int i = 0; i < operation.operationArgumentTypes.length; i++) {
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
                        b.startStatement().startCall("builtNodes.add").startGroup().cast(operationNodeGen.asType()).string("node").end().end().end();
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
            b.startStatement().string("nodes.setNodes(builtNodes.toArray(new ").type(operationNodeGen.asType()).string("[0]))").end();
            b.end();

            b.startIf().string("withSource").end().startBlock();

            TypeMirror sourceArray = arrayOf(types.Source);
            CodeTree toArray = CodeTreeBuilder.createBuilder().string("sources.toArray(new ").type(types.Source).string("[0])").build();
            b.declaration(sourceArray, "sourceArray", toArray);
            buildFence(b);
            b.startStatement().string("nodes.setSources(sourceArray)").end();
            b.end();

            return ex;
        }

        private CodeExecutableElement createCreateLocal() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), types.BytecodeLocal, "createLocal");
            CodeTreeBuilder b = ex.createBuilder();

            b.startReturn().startCall("createLocal").string("null").end(2);

            return ex;
        }

        private CodeExecutableElement createCreateLocalWithName() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), types.BytecodeLocal, "createLocal");
            ex.addParameter(new CodeVariableElement(context.getDeclaredType(Object.class), "name"));
            CodeTreeBuilder b = ex.createBuilder();

            if (model.enableSerialization) {
                b.startIf().string("serialization != null").end().startBlock();
                serializationWrapException(b, () -> {
                    serializationElements.writeShort(b, serializationElements.codeCreateLocal);
                    // TODO: serialize the name
                });
                b.end();
            }
            b.statement("localNames.add(name)");
            b.startReturn().startNew(bytecodeLocalImpl.asType()).string("numLocals++").end(2);

            return ex;
        }

        private CodeExecutableElement createCreateLabel() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), types.BytecodeLabel, "createLabel");
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
            buildThrowIllegalStateException(b, "\"Labels must be created inside either Block or Root operations.\"");
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
            buildThrowIllegalStateException(b, "\"BytecodeLabel was emitted at a position with a different stack height than a branch instruction that targets it. Branches must be balanced.\"");
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
            b.string("opSeqNum++");
            b.string("0");
            b.string("null");
            b.end(2);

            return ex;
        }

        private CodeExecutableElement createBegin(OperationModel operation) {
            if (operation.kind == OperationKind.ROOT) {
                return createBeginRoot(operation);
            }
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), context.getType(void.class), "begin" + operation.name);

            for (CodeVariableElement param : operation.getOperationArguments()) {
                ex.addParameter(param);
            }
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

            if (model.enableSerialization) {
                b.startIf().string("serialization != null").end().startBlock();
                createSerializeBegin(operation, b);
                b.statement("return");
                b.end();
            }

            if (operation.isSourceOnly()) {
                b.startIf().string("!withSource").end().startBlock();
                b.returnStatement();
                b.end();
            }

            b.startStatement().startCall("beforeChild").end(2);
            b.startStatement().startCall("beginOperation");
            b.tree(createOperationConstant(operation));
            buildOperationBeginData(b, operation);
            b.end(2);

            switch (operation.kind) {
                case SOURCE:
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
                    buildThrowIllegalStateException(b, "\"No enclosing Source operation found - each SourceSection must be enclosed in a Source operation.\"");
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
                    b.startAssign("finallyTryContext").startNew(finallyTryContext.asType());
                    for (CodeVariableElement field : builderContextSensitiveState) {
                        b.string(field.getName());
                    }
                    b.string("opSeqNum - 1");
                    b.string("new HashSet<>()");
                    b.string("finallyTryContext");
                    b.end(2);
                    b.statement("((Object[]) operationStack[operationSp - 1].data)[1] = finallyTryContext");

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
            b.statement("operationStack = new OperationStackEntry[8]");
            b.statement("opSeqNum = 0");
            b.statement("operationSp = 0");

            b.statement("numLocals = " + USER_LOCALS_START_IDX);
            b.statement("numLabels = 0");
            b.statement("numNodes = 0");
            b.statement("constantPool = new ConstantPool()");
            b.statement("localNames = new ArrayList<>()");

            b.startIf().string("withSource").end().startBlock();
            b.statement("sourceIndexStack = new int[1]");
            b.statement("sourceIndexSp = 0");
            b.statement("sourceLocationStack = new int[12]");
            b.statement("sourceLocationSp = 0");
            b.end();

            b.startStatement().startCall("beginOperation");
            b.tree(createOperationConstant(rootOperation));
            b.startNewArray(new ArrayCodeTypeMirror(context.getType(Object.class)), null);
            b.string("false"); // TODO: are we using this?
            b.string("language");
            b.end(3);

            return ex;
        }

        private void createSerializeBegin(OperationModel operation, CodeTreeBuilder b) {
            serializationWrapException(b, () -> {

                CodeTreeBuilder after = CodeTreeBuilder.createBuilder();
                for (int i = 0; i < operation.operationArgumentTypes.length; i++) {
                    TypeMirror argType = operation.operationArgumentTypes[i];
                    String argumentName = operation.getOperationArgumentName(i);
                    if (ElementUtils.typeEquals(argType, types.TruffleLanguage)) {
                        b.statement("serialization.language = language");
                    } else if (ElementUtils.typeEquals(argType, types.BytecodeLocal)) {
                        serializationElements.writeShort(after, "(short) ((BytecodeLocalImpl) " + argumentName + ").index");
                    } else if (ElementUtils.typeEquals(argType, new ArrayCodeTypeMirror(types.BytecodeLocal))) {
                        serializationElements.writeShort(after, "(short) " + argumentName + ".length");
                        after.startFor().string("int i = 0; i < arg" + i + ".length; i++").end().startBlock();
                        serializationElements.writeShort(after, "(short) ((BytecodeLocalImpl) " + argumentName + "[i]).index");
                        after.end();
                    } else if (ElementUtils.typeEquals(argType, types.BytecodeLabel)) {
                        serializationElements.writeShort(after, "(short) ((BytecodeLabelImpl) " + argumentName + ").declaringOp");
                    } else if (ElementUtils.typeEquals(argType, context.getType(int.class))) {
                        serializationElements.writeInt(after, argumentName);
                    } else if (operation.kind == OperationKind.INSTRUMENT_TAG && i == 0) {
                        serializationElements.writeShort(after, "CLASS_TO_TAG_INDEX.get(" + operation.getOperationArgumentName(0) + ")");
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

        private void buildOperationBeginData(CodeTreeBuilder b, OperationModel operation) {
            if (operation.isTransparent) {
                b.string("new Object[]{false /* value produced */, (int) " + UNINIT + " /* child bci */}");
                return;
            }
            switch (operation.kind) {
                case IF_THEN:
                    b.string("new int[]{" + UNINIT + " /* false branch fix-up index */}");
                    break;
                case IF_THEN_ELSE:
                case CONDITIONAL:
                    b.string("new int[]{" + UNINIT + " /* false branch fix-up index */, " + UNINIT + " /* end branch fix-up index */ }");
                    break;
                case WHILE:
                    b.string("new int[]{bci /* while start */, " + UNINIT + " /* end branch fix-up index */}");
                    break;
                case STORE_LOCAL:
                case STORE_LOCAL_MATERIALIZED:
                case LOAD_LOCAL_MATERIALIZED:
                    b.string(operation.getOperationArgumentName(0));
                    break;
                case CUSTOM_SIMPLE:
                    b.startNewArray(arrayOf(context.getType(Object.class)), null);
                    for (InstructionImmediate immediate : operation.instruction.getImmediates(ImmediateKind.BYTECODE_INDEX)) {
                        b.string(UNINIT + " /* " + immediate.name + " */");
                    }
                    for (int i = 0; i < operation.operationArgumentTypes.length; i++) {
                        b.string(operation.getOperationArgumentName(i));
                    }
                    b.end();
                    break;
                case CUSTOM_SHORT_CIRCUIT:
                    b.startNewArray(arrayOf(context.getType(Object.class)), null);
                    b.string("new int[0] /* branch fix-up indices */");
                    ShortCircuitInstructionModel shortCircuitInstruction = (ShortCircuitInstructionModel) operation.instruction;
                    if (shortCircuitInstruction.booleanConverterInstruction.signature.canBoxingEliminateValue(0)) {
                        b.string(UNINIT + " /* child bci (for boolean converter) */");
                    }
                    b.end();
                    break;
                case TRY_CATCH:
                    b.string("new int[]{" +
                                    "bci /* try start */, " +
                                    UNINIT + " /* try end */, " +
                                    UNINIT + " /* catch start */, " +
                                    UNINIT + " /* branch past catch fix-up index */, " +
                                    "curStack /* entry stack height */, " +
                                    "((BytecodeLocalImpl) " + operation.getOperationArgumentName(0) + ").index /* exception local index */}");
                    break;
                case FINALLY_TRY:
                    b.string("new Object[]{ " + operation.getOperationArgumentName(0) + " /* exception local */, null /* finallyTryContext */}");
                    break;
                case FINALLY_TRY_NO_EXCEPT:
                    b.string("new Object[]{ null /* exception local */, null /* finallyTryContext */}");
                    break;
                default:
                    b.string("null");
                    break;
            }
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
                b.startIf().string("!withSource").end().startBlock();
                b.returnStatement();
                b.end();
            }

            b.startStatement().startCall("endOperation");
            b.tree(createOperationConstant(operation));
            b.end(2);

            if (operation.kind == OperationKind.CUSTOM_SHORT_CIRCUIT) {
                // Short-circuiting operations should have at least one child.
                b.startIf().string("operationStack[operationSp].childCount == 0").end().startBlock();
                buildThrowIllegalStateException(b, "\"Operation " + operation.name + " expected at least " + childString(1) +
                                ", but \" + operationStack[operationSp].childCount + \" provided. This is probably a bug in the parser.\"");
                b.end();
            } else if (operation.isVariadic && operation.numChildren > 1) {
                // The variadic child is included in numChildren, so the operation requires
                // numChildren - 1 children at minimum.
                b.startIf().string("operationStack[operationSp].childCount < " + (operation.numChildren - 1)).end().startBlock();
                buildThrowIllegalStateException(b, "\"Operation " + operation.name + " expected at least " + childString(operation.numChildren - 1) +
                                ", but \" + operationStack[operationSp].childCount + \" provided. This is probably a bug in the parser.\"");
                b.end();
            } else if (!operation.isVariadic) {
                b.startIf().string("operationStack[operationSp].childCount != " + operation.numChildren).end().startBlock();
                buildThrowIllegalStateException(b, "\"Operation " + operation.name + " expected exactly " + childString(operation.numChildren) +
                                ", but \" + operationStack[operationSp].childCount + \" provided. This is probably a bug in the parser.\"");
                b.end();
            }

            switch (operation.kind) {
                case CUSTOM_SHORT_CIRCUIT:
                    ShortCircuitInstructionModel shortCircuitInstruction = (ShortCircuitInstructionModel) operation.instruction;
                    if (shortCircuitInstruction.returnConvertedValue) {
                        /*
                         * All operands except the last are automatically converted when testing the
                         * short circuit condition. For the last operand we need to insert a
                         * conversion.
                         */
                        buildEmitBooleanConverterInstruction(b, shortCircuitInstruction, "operationStack[operationSp].data");
                    }
                    if (model.enableTracing) {
                        b.statement("basicBlockBoundary[bci] = true");
                    }
                    // Go through the work list and fill in the branch target for each branch.
                    b.startFor().string("int site : (int[]) ((Object[]) operationStack[operationSp].data)[0]").end().startBlock();
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
                case CONDITIONAL:
                case WHILE:
                    if (model.enableTracing) {
                        b.statement("basicBlockBoundary[bci] = true");
                    }
                    break;
                case FINALLY_TRY:
                    b.statement("Object[] finallyTryData = (Object[]) operationStack[operationSp].data");
                    b.statement("BytecodeLocalImpl exceptionLocal = (BytecodeLocalImpl) finallyTryData[0]");
                    b.statement("FinallyTryContext ctx = (FinallyTryContext) finallyTryData[1]");
                    b.statement("short exceptionIndex = (short) exceptionLocal.index");
                    b.statement("int exHandlerIndex = doCreateExceptionHandler(ctx.bci, bci, " + UNINIT + " /* handler start */, curStack, exceptionIndex)");
                    b.lineComment("emit handler for normal completion case");
                    b.statement("doEmitFinallyHandler(ctx)");
                    b.statement("int endBranchIndex = bci + 1");
                    // Skip over the catch handler. This branch could be relative if the current
                    // FinallyTry is nested in another FinallyTry handler.
                    // (NB: By the time we invoke endFinallyTry, our finallyTryContext has been
                    // restored to point to the parent context.)
                    emitFinallyRelativeBranchCheck(b, 1);
                    buildEmitInstruction(b, model.branchInstruction, new String[]{UNINIT});

                    b.lineComment("emit handler for exceptional case");
                    b.statement("exHandlers[exHandlerIndex + 2] = bci /* handler start */");
                    b.statement("doEmitFinallyHandler(ctx)");
                    buildEmitInstruction(b, model.throwInstruction, new String[]{"exceptionIndex"});
                    b.statement(writeBc("endBranchIndex", "(short) bci"));
                    break;
                case FINALLY_TRY_NO_EXCEPT:
                    b.statement("Object[] finallyTryData = (Object[]) operationStack[operationSp].data");
                    b.statement("FinallyTryContext ctx = (FinallyTryContext) finallyTryData[1]");
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

            b.startStatement().startCall("afterChild");
            if (operation.isTransparent) {
                b.string("(boolean) ((Object[]) operationStack[operationSp].data)[0]");
                b.string("(int) ((Object[]) operationStack[operationSp].data)[1]");
            } else {
                b.string(Boolean.toString(!operation.isVoid));
                if (operation.instruction != null) {
                    b.string("bci - " + operation.instruction.getInstructionLength());
                } else {
                    b.string("-1");
                }
            }
            b.end(2);

            return ex;
        }

        private CodeExecutableElement createEndRoot(OperationModel rootOperation) {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), model.templateType.asType(), "endRoot");
            CodeTreeBuilder b = ex.getBuilder();

            if (model.enableSerialization) {
                b.startIf().string("serialization != null").end().startBlock();
                serializationWrapException(b, () -> {
                    CodeTree constructorCall = CodeTreeBuilder.createBuilder().startNew(operationNodeGen.asType()).string("serialization.language").startStaticCall(types.FrameDescriptor,
                                    "newBuilder").end(2).build();
                    b.declaration(operationNodeGen.asType(), "node", constructorCall);

                    b.statement("node.buildIndex = buildIndex++");
                    serializationElements.writeShort(b, serializationElements.codeEnd[rootOperation.id]);
                    b.statement("builtNodes.add(node)");
                    b.statement("return node");
                });
                b.end();
            }

            b.startStatement().startCall("endOperation");
            b.tree(createOperationConstant(rootOperation));
            b.end(2);

            b.declaration(operationNodeGen.asType(), "result", (CodeTree) null);
            b.startIf().string("isReparse").end().startBlock(); // {
            b.statement("result = builtNodes.get(buildIndex)");

            b.startAssert().string("result.buildIndex == buildIndex").end();

            b.end().startElseBlock(); // } {

            b.declaration("Object[]", "rootData", "((Object[]) operationStack[operationSp].data)");
            b.startStatement();
            b.type(types.TruffleLanguage).string("language = ");
            b.cast(types.TruffleLanguage).string("rootData[1]");
            b.end();

            // Construct the frame descriptor.
            CodeTree newBuilderCall = CodeTreeBuilder.createBuilder().startStaticCall(types.FrameDescriptor, "newBuilder").string("numLocals + maxStack").end().build();
            b.declaration(types.FrameDescriptor_Builder, "fdb", newBuilderCall);

            if (userLocalsStartIndex > 0) {
                // Allocate reserved slots
                b.startStatement().startCall("fdb.addSlots");
                b.string(USER_LOCALS_START_IDX);
                b.staticReference(types.FrameSlotKind, "Illegal");
                b.end(2);
            }

            // Allocate user locals
            b.startFor().string("Object name : localNames").end().startBlock();
            b.startStatement().startCall("fdb.addSlot");
            b.staticReference(types.FrameSlotKind, "Illegal");
            b.string("name");
            b.string("null"); // description
            b.end(2);
            b.end();

            // Allocate stack
            b.startStatement().startCall("fdb.addSlots");
            b.string("maxStack");
            b.staticReference(types.FrameSlotKind, "Illegal");
            b.end(2);

            b.startAssign("result").startNew(operationNodeGen.asType());
            b.string("language");
            b.string("fdb");
            b.end(2);

            b.startStatement().startCall("result", "setInterpreterState");
            b.string("nodes");
            b.string("Arrays.copyOf(bc, bci)"); // bc
            b.string("constantPool.toArray()"); // constants
            b.string("Arrays.copyOf(exHandlers, exHandlerCount)"); // handlers
            b.string("numLocals");
            b.string("numNodes");
            b.string("numConditionalBranches");
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

            b.startIf().string("withSource").end().startBlock();
            CodeTree copyOf = CodeTreeBuilder.createBuilder().startCall("Arrays.copyOf").string("sourceInfo").string("sourceInfoIndex").end().build();
            b.declaration(arrayOf(context.getType(int.class)), "sourceInfoArray", copyOf);
            buildFence(b);
            b.statement("result.sourceInfo = sourceInfoArray");
            b.end();

            b.startIf().string("savedState == null").end().startBlock(); // {
            b.lineComment("this signifies that there is no root node currently being built");
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

        private void buildEmitOperationInstruction(CodeTreeBuilder b, OperationModel operation) {
            String[] args = switch (operation.kind) {
                case LOAD_LOCAL -> new String[]{"((BytecodeLocalImpl) " + operation.getOperationArgumentName(0) + ").index"};
                case STORE_LOCAL, LOAD_LOCAL_MATERIALIZED, STORE_LOCAL_MATERIALIZED -> new String[]{"((BytecodeLocalImpl) operationStack[operationSp].data).index"};
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
                    buildThrowIllegalStateException(b, "\"Branch must be targeting a label that is declared in an enclosing operation. Jumps into other operations are not permitted.\"");
                    b.end();

                    b.statement("doEmitLeaves(labelImpl.declaringOp)");

                    b.startIf().string("labelImpl.isDefined()").end().startBlock();
                    buildThrowIllegalStateException(b, "\"Backward branches are unsupported. Use a While operation to model backward control flow.\"");
                    b.end();
                    // Mark the branch target as uninitialized. Add this location to a work list to
                    // be processed once the label is defined.
                    b.startStatement().startCall("registerUnresolvedLabel");
                    b.string("labelImpl");
                    b.string("bci + 1");
                    b.string("curStack");
                    b.end(2);
                    b.newLine();

                    // Branches inside finally handlers can only be relative to the handler,
                    // otherwise a finally handler emitted before a "return" could branch out of the
                    // handler and circumvent the return.
                    b.startIf().string("inFinallyTryHandler(finallyTryContext)").end().startBlock();

                    b.startIf().string("labelImpl.finallyTryOp != finallyTryContext.finallyTrySequenceNumber").end().startBlock();
                    buildThrowIllegalStateException(b, "\"Branches inside finally handlers can only target labels defined in the same handler.\"");
                    b.end();

                    b.lineComment("We need to track branch targets inside finally handlers so that they can be adjusted each time the handler is emitted.");
                    b.statement("finallyTryContext.finallyRelativeBranches.add(bci + 1)");

                    b.end();
                    yield new String[]{UNINIT};
                }
                case YIELD -> {
                    b.statement("ContinuationLocation continuation = new ContinuationLocationImpl(numYields++, bci + 2, curStack)");
                    b.statement("continuationLocations.add(continuation)");
                    yield new String[]{"constantPool.addConstant(continuation)"};
                }
                case CUSTOM_SIMPLE -> buildCustomInitializer(b, operation, operation.instruction);
                case CUSTOM_SHORT_CIRCUIT -> buildCustomShortCircuitInitializer(b, operation, operation.instruction);
                default -> throw new AssertionError("Reached an operation " + operation.name + " that cannot be initialized. This is a bug in the Operation DSL processor.");
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

            for (CodeVariableElement param : operation.getOperationArguments()) {
                ex.addParameter(param);
            }

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
                buildThrowIllegalStateException(b, "\"BytecodeLabel already emitted. Each label must be emitted exactly once.\"");
                b.end();

                b.startIf().string("labelImpl.declaringOp != operationStack[operationSp - 1].sequenceNumber").end().startBlock();
                buildThrowIllegalStateException(b, "\"BytecodeLabel must be emitted inside the same operation it was created in.\"");
                b.end();

                b.statement("labelImpl.bci = bci");
                b.startStatement().startCall("resolveUnresolvedLabel");
                b.string("labelImpl");
                b.string("curStack");
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

            List<InstructionImmediate> immediates = instruction.getImmediates();
            String[] args = new String[immediates.size()];

            int childNodeIndex = 0;
            int localSetterIndex = 0;
            int localSetterRangeIndex = 0;
            for (int i = 0; i < immediates.size(); i++) {
                InstructionImmediate immediate = immediates.get(i);
                args[i] = switch (immediate.kind) {
                    case BYTECODE_INDEX -> {
                        String child = "child" + childNodeIndex++;
                        b.startAssign("int " + child);
                        b.string("(int) ((Object[]) operationStack[operationSp].data)[" + i + "]");
                        b.end();

                        yield child;
                    }
                    case LOCAL_SETTER -> {
                        int currentSetterIndex = localSetterIndex++;
                        String arg = "localSetter" + currentSetterIndex;
                        b.startAssign("int " + arg);
                        if (inEmit) {
                            b.string("((BytecodeLocalImpl) " + operation.getOperationArgumentName(currentSetterIndex) + ").index");
                        } else {
                            b.string("((BytecodeLocalImpl)((Object[]) operationStack[operationSp].data)[" + i + "]).index");
                        }
                        b.end();
                        b.startStatement();
                        b.startStaticCall(types.LocalSetter, "create");
                        b.string(arg);
                        b.end(2);

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
                            b.string("(BytecodeLocal[]) ((Object[]) operationStack[operationSp].data)[" + (localSetterIndex + localSetterRangeIndex + i) + "]");
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
                    case NODE -> "allocateNode()";
                    case INTEGER, CONSTANT, PROFILE -> throw new AssertionError("Operation " + operation.name + " takes an immediate " + immediate.name + " with unexpected kind " + immediate.kind +
                                    ". This is a bug in the Operation DSL processor.");
                };
            }

            return args;
        }

        private String[] buildCustomShortCircuitInitializer(CodeTreeBuilder b, OperationModel operation, InstructionModel instruction) {
            assert operation.kind == OperationKind.CUSTOM_SHORT_CIRCUIT;

            b.statement("int branchTarget = " + UNINIT);
            b.lineComment("Add this location to a work list to be processed once the branch target is known.");
            b.statement("int[] sites = (int[]) ((Object[]) data)[0]");
            b.statement("sites = Arrays.copyOf(sites, sites.length + 1)");
            InstructionImmediate branchIndex = instruction.getImmediate(ImmediateKind.BYTECODE_INDEX);
            b.statement("sites[sites.length-1] = bci + " + branchIndex.offset);
            b.statement("((Object[]) data)[0] = sites");
            return new String[]{"branchTarget"};
        }

        private CodeExecutableElement createBeforeChild() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "beforeChild");
            CodeTreeBuilder b = ex.createBuilder();

            createCheckRoot(b);

            b.statement("Object data = operationStack[operationSp - 1].data");
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
                b.startIf().string("(boolean) ((Object[]) data)[0]").end().startBlock();
                buildEmitInstruction(b, model.popInstruction, null);
                b.end();
                b.statement("break");
                b.end();
            }

            if (shortCircuitOperations != null) {
                for (OperationModel op : shortCircuitOperations) {
                    b.startCase().tree(createOperationConstant(op)).end().startBlock();

                    b.startIf().string("childIndex != 0").end().startBlock();
                    ShortCircuitInstructionModel shortCircuitInstruction = (ShortCircuitInstructionModel) op.instruction;
                    if (!shortCircuitInstruction.returnConvertedValue) {
                        // DUP so the boolean converter doesn't clobber the original value.
                        buildEmitInstruction(b, model.dupInstruction, null);
                    }
                    buildEmitBooleanConverterInstruction(b, shortCircuitInstruction, "data");
                    String[] args = buildCustomShortCircuitInitializer(b, op, op.instruction);
                    buildEmitInstruction(b, op.instruction, args);
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
            buildThrow(b, AssertionError.class, "\"beforeChild should not be called on an operation with no children.\"");
            b.end();

            b.end(); // switch

            return ex;
        }

        private void buildEmitBooleanConverterInstruction(CodeTreeBuilder b, ShortCircuitInstructionModel shortCircuitInstruction, String operationData) {
            InstructionModel booleanConverter = shortCircuitInstruction.booleanConverterInstruction;

            List<InstructionImmediate> immediates = booleanConverter.getImmediates();
            String[] args = new String[immediates.size()];
            for (int i = 0; i < args.length; i++) {
                InstructionImmediate immediate = immediates.get(i);
                args[i] = switch (immediate.kind) {
                    case BYTECODE_INDEX -> {
                        b.statement("int childBci = (int) ((Object[]) " + operationData + ")[1]");
                        b.startAssert();
                        b.string("childBci != " + UNINIT);
                        b.end();
                        yield "childBci";
                    }
                    case NODE -> "allocateNode()";
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

            b.statement("Object data = operationStack[operationSp - 1].data");
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
            b.statement("((Object[]) data)[0] = producedValue");
            b.statement("((Object[]) data)[1] = childBci");
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
                        buildEmitInstruction(b, model.popInstruction, null);
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
                        buildEmitInstruction(b, model.popInstruction, null);
                        b.end();
                    }
                }

                switch (op.kind) {
                    case IF_THEN:
                        b.startIf().string("childIndex == 0").end().startBlock();
                        b.statement("((int[]) data)[0] = bci + 1");
                        emitFinallyRelativeBranchCheck(b, 1);
                        buildEmitInstruction(b, model.branchFalseInstruction, new String[]{UNINIT, "allocateBranchProfile()"});
                        b.end().startElseBlock();
                        b.statement("int toUpdate = ((int[]) data)[0]");
                        b.statement(writeBc("toUpdate", "(short) bci"));
                        b.end();
                        if (model.enableTracing) {
                            b.statement("basicBlockBoundary[bci] = true");
                        }
                        break;
                    case CONDITIONAL:
                    case IF_THEN_ELSE:
                        b.startIf().string("childIndex == 0").end().startBlock();
                        b.statement("((int[]) data)[0] = bci + 1");
                        emitFinallyRelativeBranchCheck(b, 1);
                        buildEmitInstruction(b, model.branchFalseInstruction, new String[]{UNINIT, "allocateBranchProfile()"});
                        b.end().startElseIf().string("childIndex == 1").end().startBlock();
                        b.statement("((int[]) data)[1] = bci + 1");
                        emitFinallyRelativeBranchCheck(b, 1);
                        buildEmitInstruction(b, model.branchInstruction, new String[]{UNINIT});
                        if (op.kind == OperationKind.CONDITIONAL) {
                            // we have to adjust the stack for the third child
                            b.statement("curStack -= 1");
                        }
                        b.statement("int toUpdate = ((int[]) data)[0]");
                        b.statement(writeBc("toUpdate", "(short) bci"));
                        b.end().startElseBlock();
                        b.statement("int toUpdate = ((int[]) data)[1]");
                        b.statement(writeBc("toUpdate", "(short) bci"));
                        b.end();
                        if (model.enableTracing) {
                            b.statement("basicBlockBoundary[bci] = true");
                        }
                        break;
                    case WHILE:
                        b.startIf().string("childIndex == 0").end().startBlock();
                        b.statement("((int[]) data)[1] = bci + 1");
                        emitFinallyRelativeBranchCheck(b, 1);
                        buildEmitInstruction(b, model.branchFalseInstruction, new String[]{UNINIT, "allocateBranchProfile()"});
                        b.end().startElseBlock();
                        emitFinallyRelativeBranchCheck(b, 1);
                        buildEmitInstruction(b, model.branchBackwardInstruction, new String[]{"(short) ((int[]) data)[0]"});
                        b.statement("int toUpdate = ((int[]) data)[1]");
                        b.statement(writeBc("toUpdate", "(short) bci"));
                        b.end();
                        if (model.enableTracing) {
                            b.statement("basicBlockBoundary[bci] = true");
                        }
                        break;
                    case TRY_CATCH:
                        b.statement("int[] dArray = (int[]) data");
                        b.startIf().string("childIndex == 0").end().startBlock();
                        b.statement("dArray[1] = bci /* try end */");
                        b.statement("dArray[3] = bci + 1 /* branch past catch fix-up index */");
                        emitFinallyRelativeBranchCheck(b, 1);
                        buildEmitInstruction(b, model.branchInstruction, new String[]{UNINIT});
                        b.statement("dArray[2] = bci /* catch start */");
                        b.end();
                        b.startElseIf().string("childIndex == 1").end().startBlock();
                        b.statement("int toUpdate = dArray[3] /* branch past catch fix-up index */");
                        b.statement(writeBc("toUpdate", "(short) bci"));
                        b.statement("doCreateExceptionHandler(dArray[0], dArray[1], dArray[2], dArray[4], dArray[5])");
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
                                        CodeTreeBuilder.createBuilder().startStaticCall(operationBuilderType, "createBranchLabelMapping").string("unresolvedLabels").end());
                        b.declaration(
                                        generic(HashMap.class, context.getDeclaredType(Integer.class), context.getDeclaredType(Integer.class)),
                                        "unresolvedBranchStackHeights",
                                        CodeTreeBuilder.createBuilder().startStaticCall(operationBuilderType, "createBranchStackHeightMapping").string("unresolvedLabels").end());

                        b.startStatement().startCall("finallyTryContext", "setHandler");
                        b.string("Arrays.copyOf(bc, bci)");
                        b.string("maxStack");
                        b.string("withSource ? Arrays.copyOf(sourceInfo, sourceInfoIndex) : null");
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
                    case CUSTOM_SIMPLE:
                        int immediateIndex = 0;
                        boolean elseIf = false;
                        for (int valueIndex = 0; valueIndex < op.instruction.signature.valueCount; valueIndex++) {
                            if (op.instruction.signature.canBoxingEliminateValue(valueIndex)) {
                                elseIf = b.startIf(elseIf);
                                b.string("childIndex == " + valueIndex).end().startBlock();
                                b.statement("((Object[]) data)[" + immediateIndex++ + "] = childBci");
                                b.end();
                            }
                        }
                        break;
                    case CUSTOM_SHORT_CIRCUIT:
                        ShortCircuitInstructionModel shortCircuitInstruction = (ShortCircuitInstructionModel) op.instruction;
                        if (shortCircuitInstruction.booleanConverterInstruction.signature.canBoxingEliminateValue(0)) {
                            b.statement("((Object[]) data)[1] = childBci");
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

            b.startIf().string("withSource && sourceInfoIndex + context.handlerSourceInfo.length > sourceInfo.length").end().startBlock();
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
            Set<InstructionKind> relocatable = Set.of(InstructionKind.BRANCH, InstructionKind.BRANCH_BACKWARD, InstructionKind.BRANCH_FALSE, InstructionKind.YIELD);
            Map<Boolean, List<InstructionModel>> builtinsGroupedByNeedsRelocation = model.getInstructions().stream().filter(instr -> !instr.isCustomInstruction()).collect(
                            Collectors.partitioningBy(instr -> relocatable.contains(instr.kind)));
            Map<Integer, List<InstructionModel>> nonRelocatingBuiltinsGroupedByLength = builtinsGroupedByNeedsRelocation.get(false).stream().collect(
                            Collectors.groupingBy(InstructionModel::getInstructionLength));
            Map<Integer, List<InstructionModel>> customInstructionsGroupedByEncoding = model.getInstructions().stream().filter(InstructionModel::isCustomInstruction).collect(
                            Collectors.groupingBy(instr -> (instr.kind == InstructionKind.CUSTOM_SHORT_CIRCUIT) ? 0 : instr.id));

            // Non-relocatable builtins (one case per instruction length)
            for (Map.Entry<Integer, List<InstructionModel>> entry : nonRelocatingBuiltinsGroupedByLength.entrySet()) {
                for (InstructionModel instr : entry.getValue()) {
                    b.startCase().tree(createInstructionConstant(instr)).end();
                }
                b.startBlock();
                b.statement("handlerBci += " + entry.getKey());
                b.statement("break");
                b.end();
            }

            // Relocatable builtins (one case each)
            for (InstructionModel instr : builtinsGroupedByNeedsRelocation.get(true)) {
                b.startCase().tree(createInstructionConstant(instr)).end().startBlock();

                switch (instr.kind) {
                    case BRANCH:
                    case BRANCH_BACKWARD:
                    case BRANCH_FALSE:
                        b.statement("int branchIdx = handlerBci + 1"); // BCI of branch immediate
                        b.statement("short branchTarget = " + readHandlerBc("branchIdx"));

                        if (instr.kind == InstructionKind.BRANCH_BACKWARD) {
                            // Backward branches are only used internally by while loops. They
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
                            b.string("curStack + sp");
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
                        break;

                    case YIELD:
                        b.statement("int locationBci = handlerBci + 1");
                        b.statement("ContinuationLocationImpl cl = (ContinuationLocationImpl) constantPool.getConstant(" + readHandlerBc("locationBci") + ")");
                        // The continuation should resume after this yield instruction
                        b.statement("assert cl.bci == locationBci + 1");
                        b.statement("ContinuationLocationImpl newContinuation = new ContinuationLocationImpl(numYields++, offsetBci + cl.bci, curStack + cl.sp)");
                        b.statement(writeBc("offsetBci + locationBci", "(short) constantPool.addConstant(newContinuation)"));
                        b.statement("continuationLocations.add(newContinuation)");
                        break;
                    default:
                        // do nothing
                        break;
                }

                b.statement("handlerBci += " + instr.getInstructionLength());
                b.statement("break");
                b.end();

            }

            // One case per custom instruction (except short circuit instructions, which all have
            // the same encoding).
            for (List<InstructionModel> instrs : customInstructionsGroupedByEncoding.values()) {
                for (InstructionModel instr : instrs) {
                    b.startCase().tree(createInstructionConstant(instr)).end();
                }
                b.startBlock();

                InstructionModel instr = instrs.get(0);
                List<InstructionImmediate> immediates = instr.getImmediates();
                for (int i = 0; i < immediates.size(); i++) {
                    InstructionImmediate immediate = immediates.get(i);
                    switch (immediate.kind) {
                        case BYTECODE_INDEX:
                            // Custom operations don't have non-local branches/children, so
                            // this immediate is *always* relative.
                            b.statement(writeBc("offsetBci + handlerBci + " + immediate.offset,
                                            "(short) (" + readBc("offsetBci + handlerBci + " + immediate.offset) + " + offsetBci) /* adjust " + immediate.name + " */"));
                            break;
                        case NODE:
                            // Allocate a separate Node for each handler.
                            b.statement(writeBc("offsetBci + handlerBci + " + immediate.offset, "(short) allocateNode()"));
                            break;
                        default:
                            // do nothing
                            break;
                    }
                }

                b.statement("handlerBci += " + instr.getInstructionLength());
                b.statement("break");
                b.end();
            }

            b.end(); // switch
            b.end(); // for

            b.statement("bci += handlerBc.length");

            b.startIf().string("curStack + context.handlerMaxStack > maxStack").end().startBlock();
            b.statement("maxStack = curStack + context.handlerMaxStack");
            b.end();

            b.startIf().string("withSource").end().startBlock();
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
            b.statement("exHandlers[exHandlerCount + idx + 3] = context.handlerExHandlers[idx + 3] + curStack");
            b.statement("exHandlers[exHandlerCount + idx + 4] = context.handlerExHandlers[idx + 4]");
            b.end();

            b.statement("exHandlerCount += context.handlerExHandlers.length");

            return ex;
        }

        private CodeExecutableElement createDoEmitInstruction() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "doEmitInstruction");
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "instr"));
            ex.addParameter(new CodeVariableElement(new ArrayCodeTypeMirror(context.getType(int.class)), "data"));
            ex.setVarArgs(true);

            CodeTreeBuilder b = ex.createBuilder();

            b.statement("int lastIndex = bci + data.length");
            b.startIf().string("bc.length - 1 < lastIndex").end().startBlock();

            b.statement("int newLength = bc.length * 2");
            b.startWhile().string("newLength - 1 < lastIndex").end().startBlock();
            b.statement("newLength *= 2");
            b.end();

            b.startAssign("bc").startStaticCall(context.getType(Arrays.class), "copyOf");
            b.string("bc");
            b.string("newLength");
            b.end(2);
            if (model.enableTracing) {
                // since we can mark a start of the BB before it's first instruction is emitted,
                // basicBlockBoundary must always be at least 1 longer than `bc` array to prevent
                // ArrayIndexOutOfBoundsException
                b.startAssign("basicBlockBoundary").startStaticCall(context.getType(Arrays.class), "copyOf");
                b.string("basicBlockBoundary");
                b.string("newLength + 1");
                b.end(2);
            }

            b.end();

            b.statement(writeBc("bci++", "(short) instr"));
            b.startFor().string("int i = 0; i < data.length; i++").end().startBlock();
            b.statement(writeBc("bci++", "(short) data[i]"));
            b.end();

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

            b.startIf().string("curStack + 1 > maxStack").end().startBlock();
            b.statement("maxStack = curStack + 1");
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

            b.statement("curStack -= count - 1");
            b.startIf().string("count == 0 && curStack > maxStack").end().startBlock();
            b.statement("maxStack = curStack");
            b.end();

            return ex;
        }

        private void buildEmitInstruction(CodeTreeBuilder b, InstructionModel instr, String[] arguments) {
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
                    break;
                case BRANCH_FALSE:
                case POP:
                case STORE_LOCAL:
                    b.statement("curStack -= 1");
                    break;
                case CUSTOM:
                case CUSTOM_QUICKENED:
                    int delta = (instr.signature.isVoid ? 0 : 1) - instr.signature.valueCount;
                    if (delta != 0) {
                        b.statement("curStack += " + delta);
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
                    ShortCircuitInstructionModel shortCircuitInstruction = (ShortCircuitInstructionModel) instr;
                    if (shortCircuitInstruction.returnConvertedValue) {
                        // Stack: [..., convertedValue]
                        b.statement("curStack -= 1");
                    } else {
                        // Stack: [..., value, convertedValue]
                        b.statement("curStack -= 2");
                    }
                    break;
                case DUP:
                case LOAD_ARGUMENT:
                case LOAD_CONSTANT:
                case LOAD_LOCAL:
                    hasPositiveDelta = true;
                    b.statement("curStack += 1");
                    break;
                case STORE_LOCAL_MATERIALIZED:
                    b.statement("curStack -= 2");
                    break;
                default:
                    throw new UnsupportedOperationException();
            }

            if (hasPositiveDelta) {
                b.startIf().string("curStack > maxStack").end().startBlock();
                b.statement("maxStack = curStack");
                b.end();
            }

            b.startStatement().startCall("doEmitInstruction");
            b.tree(createInstructionConstant(instr));
            if (arguments != null) {
                for (String argument : arguments) {
                    b.string(argument);
                }
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

            b.startAssert().string("withSource").end();

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

            b.statement("FinallyTryContext ctx = (FinallyTryContext) ((Object[]) operationStack[i].data)[1]");
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

            b.startReturn();
            b.string("numNodes++");
            b.end();

            return ex;
        }

        private CodeExecutableElement createAllocateBranchProfile() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(int.class), "allocateBranchProfile");
            CodeTreeBuilder b = ex.createBuilder();

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

        private CodeExecutableElement createConstructor() {
            CodeExecutableElement ctor = new CodeExecutableElement(Set.of(PRIVATE), null, "Builder");
            ctor.addParameter(new CodeVariableElement(bytecodeNodesImpl.asType(), "nodes"));
            ctor.addParameter(new CodeVariableElement(context.getType(boolean.class), "isReparse"));
            ctor.addParameter(new CodeVariableElement(types.BytecodeConfig, "config"));

            CodeTreeBuilder b = ctor.createBuilder();

            b.statement("this.nodes = nodes");
            b.statement("this.isReparse = isReparse");
            b.statement("this.withSource = config.isWithSource()");
            b.statement("this.withInstrumentation = config.isWithInstrumentation()");
            b.statement("this.sources = this.withSource ? new ArrayList<>() : null");
            b.statement("this.builtNodes = new ArrayList<>()");

            return ctor;
        }

        private void buildContextSensitiveFieldInitializer(CodeTreeBuilder b) {
            b.statement("bc = new short[32]");
            b.statement("bci = 0");
            b.statement("curStack = 0");
            b.statement("maxStack = 0");
            b.startIf().string("withSource").end().startBlock();
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

    class BytecodeNodesImplFactory {
        private CodeTypeElement create() {
            bytecodeNodesImpl.setSuperClass(generic(types.BytecodeNodes, model.templateType.asType()));
            bytecodeNodesImpl.setEnclosingElement(operationNodeGen);

            bytecodeNodesImpl.add(createConstructor());
            bytecodeNodesImpl.add(createReparseImpl());
            bytecodeNodesImpl.add(createSetNodes());
            bytecodeNodesImpl.add(createSetSources());
            bytecodeNodesImpl.add(createGetSources());

            return bytecodeNodesImpl;
        }

        private CodeExecutableElement createConstructor() {
            CodeExecutableElement ctor = new CodeExecutableElement(null, "BytecodeNodesImpl");
            ctor.addParameter(new CodeVariableElement(parserType, "generator"));

            ctor.createBuilder().statement("super(generator)");
            return ctor;
        }

        private CodeExecutableElement createReparseImpl() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.BytecodeNodes, "reparseImpl");
            addOverride(ex);
            mergeSuppressWarnings(ex, "hiding");
            ex.renameArguments("config", "parse", "nodes");
            ((CodeVariableElement) ex.getParameters().get(2)).setType(arrayOf(model.templateType.asType()));
            CodeTreeBuilder b = ex.createBuilder();

            // When we reparse, we add metadata to the existing nodes. The builder gets them here.
            b.declaration(builder.asType(), "builder",
                            b.create().startNew(builder.asType()).string("this").string("true").string("config").end().build());

            b.startFor().type(model.templateType.asType()).string(" node : nodes").end().startBlock();
            b.startStatement().startCall("builder.builtNodes.add");
            b.startGroup().cast(operationNodeGen.asType()).string("node").end();
            b.end(2);
            b.end(2);

            b.startStatement().startCall(castParser(ex, "parse"), "parse").string("builder").end(2);

            b.startStatement().startCall("builder", "finish").end(2);

            return ex;
        }

        private CodeExecutableElement createSetNodes() {
            return GeneratorUtils.createSetter(Set.of(), new CodeVariableElement(arrayOf(operationNodeGen.asType()), "nodes"));
        }

        private CodeExecutableElement createSetSources() {
            return GeneratorUtils.createSetter(Set.of(), new CodeVariableElement(arrayOf(types.Source), "sources"));
        }

        private CodeExecutableElement createGetSources() {
            return GeneratorUtils.createGetter(Set.of(), new CodeVariableElement(arrayOf(types.Source), "sources"));
        }
    }

    // Generates an Instructions class with constants for each instruction.
    class InstructionConstantsFactory {
        private CodeTypeElement create() {
            for (InstructionModel instruction : OperationsNodeFactory.this.model.getInstructions()) {
                CodeVariableElement fld = new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), context.getType(short.class), instruction.getConstantName());
                fld.createInitBuilder().string(instruction.id).end();
                fld.createDocBuilder().startDoc().lines(instruction.pp()).end(2);
                instructionsElement.add(fld);
            }
            return instructionsElement;
        }
    }

    // Generates an Operations class with constants for each operation.
    class OperationsConstantsFactory {
        private CodeTypeElement create() {
            for (OperationModel operation : OperationsNodeFactory.this.model.getOperations()) {
                CodeVariableElement fld = new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), context.getType(int.class), operation.getConstantName());
                fld.createInitBuilder().string(operation.id).end();
                operationsElement.add(fld);
            }
            return operationsElement;
        }
    }

    class ContinueAtFactory {
        private InterpreterTier tier;

        ContinueAtFactory(InterpreterTier tier) {
            this.tier = tier;
        }

        private CodeTypeElement create() {
            CodeTypeElement interpreterType = new CodeTypeElement(Set.of(PRIVATE, STATIC), ElementKind.CLASS, null, tier.interpreterClassName());
            interpreterType.addAll(createContinueAt());
            interpreterType.add(createLoadConstantCompiled());
            return interpreterType;
        }

        private List<CodeExecutableElement> createContinueAt() {
            // This method returns a list containing the continueAt method plus helper methods for
            // custom instructions. The helper methods help reduce the bytecode size of the dispatch
            // loop.
            List<CodeExecutableElement> results = new ArrayList<>();

            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC, FINAL), context.getType(int.class), "continueAt");
            ex.addAnnotationMirror(new CodeAnnotationMirror(types.HostCompilerDirectives_BytecodeInterpreterSwitch));
            ex.addParameter(new CodeVariableElement(operationNodeGen.asType(), "$this"));
            ex.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));
            if (model.enableYield) {
                ex.addParameter(new CodeVariableElement(types.VirtualFrame, "localFrame"));
            }
            ex.addParameter(new CodeVariableElement(context.getType(short[].class), "bc"));
            ex.addParameter(new CodeVariableElement(context.getType(Object[].class), "constants"));
            if (!tier.isUncached) {
                ex.addParameter(new CodeVariableElement(new ArrayCodeTypeMirror(types.Node), "cachedNodes"));
                ex.addParameter(new CodeVariableElement(new ArrayCodeTypeMirror(context.getType(int.class)), "branchProfiles"));
            }
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "startState"));

            results.add(ex);

            CodeTreeBuilder b = ex.createBuilder();

            if (!tier.isUncached) {
                ex.addAnnotationMirror(createExplodeLoopAnnotation("MERGE_EXPLODE"));
            }

            b.statement("int bci = startState & 0xffff");
            b.statement("int sp = (startState >> 16) & 0xffff");
            b.declaration(loopCounter.asType(), "loopCounter", CodeTreeBuilder.createBuilder().startNew(loopCounter.asType()).end());
            if (model.needsBciSlot() && !model.storeBciInFrame && !tier.isUncached) {
                // If a bci slot is allocated but not used for non-uncached interpreters, set it to
                // an invalid value just in case it gets read during a stack walk.
                b.statement("ACCESS.setInt(" + localFrame() + ", " + BCI_IDX + ", -1)");
            }

            if (model.enableTracing) {
                b.declaration(context.getType(boolean[].class), "basicBlockBoundary", "$this.basicBlockBoundary");

                b.declaration(types.ExecutionTracer, "tracer",
                                CodeTreeBuilder.createBuilder().startStaticCall(types.ExecutionTracer, "get").typeLiteral(operationNodeGen.asType()).end());

                b.statement("tracer.startRoot($this)");

                b.startTryBlock();
            }

            if (tier.isUncached) {
                b.statement("int uncachedExecuteCount = $this.uncachedExecuteCount");
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

                if (instr.isInstrumentationOnly() && !tier.isInstrumented) {
                    continue;
                }

                b.startCase().tree(createInstructionConstant(instr)).end().startBlock();

                if (model.enableTracing) {
                    b.startStatement().startCall("tracer.traceInstruction");
                    b.string("bci");
                    b.string(instr.id);
                    b.string(String.valueOf(instr.signature != null && instr.signature.isVariadic));
                    b.end(2);
                }

                switch (instr.kind) {
                    case BRANCH:
                        b.statement("bci = " + readBc("bci + 1"));
                        b.statement("continue loop");
                        break;
                    case BRANCH_BACKWARD:
                        if (tier.isUncached) {
                            b.startIf().string("uncachedExecuteCount-- <= 0").end().startBlock();
                            b.tree(createTransferToInterpreterAndInvalidate("$this"));
                            b.statement("$this.changeInterpreters(CACHED)");
                            b.statement("return (sp << 16) | " + readBc("bci + 1"));
                            b.end();
                        } else {
                            emitReportLoopCount(b, CodeTreeBuilder.createBuilder().string("++loopCounter.value >= ").staticReference(loopCounter.asType(), "REPORT_LOOP_STRIDE").build(), true);

                            b.startIf().startStaticCall(types.BytecodeOSRNode, "pollOSRBackEdge").string("$this").end(2).startBlock();

                            b.startAssign("Object osrResult");
                            b.startStaticCall(types.BytecodeOSRNode, "tryOSR");
                            b.string("$this");
                            b.string("(sp << 16) | " + readBc("bci + 1")); // target
                            b.string("null"); // interpreterState
                            b.string("null"); // beforeTransfer
                            b.string("frame"); // parentFrame
                            b.end(2);

                            b.startIf().string("osrResult != null").end().startBlock();
                            b.statement(setFrameObject("sp", "osrResult"));
                            b.statement("sp++");
                            b.startReturn().string("((sp - 1) << 16) | 0xffff").end();
                            b.end();

                            b.end();
                        }
                        b.statement("bci = + " + readBc("bci + 1"));
                        b.statement("continue loop");
                        break;
                    case BRANCH_FALSE:
                        String booleanValue = "(Boolean) " + getFrameObject("sp - 1") + " == Boolean.TRUE";
                        b.startIf();
                        if (tier.isUncached) {
                            b.string(booleanValue);
                        } else {
                            b.startStaticCall(types.BytecodeSupport, "profileBranch");
                            b.string("branchProfiles");
                            b.string(readBc("bci + 2"));
                            b.string(booleanValue);
                            b.end();
                        }
                        b.end().startBlock();
                        b.statement("sp -= 1");
                        b.statement("bci += " + instr.getInstructionLength());
                        b.statement("continue loop");
                        b.end().startElseBlock();
                        b.statement("sp -= 1");
                        b.statement("bci = " + readBc("bci + 1"));
                        b.statement("continue loop");
                        b.end();
                        break;
                    case INSTRUMENTATION_ENTER:
                    case INSTRUMENTATION_EXIT:
                    case INSTRUMENTATION_LEAVE:
                        break;
                    case LOAD_ARGUMENT:
                        b.statement(setFrameObject("sp", localFrame() + ".getArguments()[" + readBc("bci + 1") + "]"));
                        b.statement("sp += 1");
                        break;
                    case LOAD_CONSTANT:
                        b.startIf().startStaticCall(types.CompilerDirectives, "inCompiledCode").end(2).startBlock();
                        b.statement("loadConstantCompiled(frame, bc, bci, sp, constants)");
                        b.end().startElseBlock();
                        b.statement(setFrameObject("sp", readConst(readBc("bci + 1"))));
                        b.end();
                        b.statement("sp += 1");
                        break;
                    case LOAD_LOCAL:
                        b.statement(setFrameObject("sp", getFrameObject(localFrame(), readBc("bci + 1"))));
                        b.statement("sp += 1");
                        break;
                    case LOAD_LOCAL_MATERIALIZED:
                        b.statement(setFrameObject("sp - 1", getFrameObject("(VirtualFrame) " + getFrameObject("sp - 1"), readBc("bci + 1"))));
                        break;
                    case POP:
                        b.statement(clearFrame("sp - 1"));
                        b.statement("sp -= 1");
                        break;
                    case DUP:
                        b.statement(copyFrameSlot("sp - 1", "sp"));
                        b.statement("sp += 1");
                        break;
                    case RETURN:
                        if (tier.isUncached) {
                            b.startIf().string("uncachedExecuteCount-- <= 0").end().startBlock();
                            b.tree(createTransferToInterpreterAndInvalidate("$this"));
                            b.statement("$this.changeInterpreters(CACHED)");
                            b.end().startElseBlock();
                            b.statement("$this.uncachedExecuteCount = uncachedExecuteCount");
                            b.end();
                        } else {
                            emitReportLoopCount(b, CodeTreeBuilder.singleString("loopCounter.value > 0"), false);
                        }
                        storeBciInFrameIfNecessary(b);

                        b.statement("return ((sp - 1) << 16) | 0xffff");
                        break;
                    case STORE_LOCAL:
                        b.statement(setFrameObject(localFrame(), readBc("bci + 1"), getFrameObject("sp - 1")));
                        b.statement(clearFrame("sp - 1"));
                        b.statement("sp -= 1");
                        break;
                    case STORE_LOCAL_MATERIALIZED:
                        b.statement("((VirtualFrame) " + getFrameObject("sp - 2") + ").setObject(" + readBc("bci + 1") + ", " + getFrameObject("sp - 1") + ")");
                        b.statement(clearFrame("sp - 1"));
                        b.statement(clearFrame("sp - 2"));
                        b.statement("sp -= 2");
                        break;
                    case THROW:
                        b.statement("throw sneakyThrow((Throwable) " + getFrameObject(localFrame(), readBc("bci + 1")) + ")");
                        break;
                    case YIELD:
                        storeBciInFrameIfNecessary(b);
                        emitReportLoopCount(b, CodeTreeBuilder.singleString("loopCounter.value > 0"), false);
                        b.statement("int numLocals = $this.numLocals");
                        b.statement(copyFrameTo("frame", "numLocals", "localFrame", "numLocals", "(sp - 1 - numLocals)"));
                        b.statement(setFrameObject("sp - 1", "((ContinuationLocation) " + readConst(readBc("bci + 1")) + ").createResult(localFrame, " + getFrameObject("sp - 1") + ")"));
                        b.statement("return (((sp - 1) << 16) | 0xffff)");
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
                    case CUSTOM_SHORT_CIRCUIT:
                        ShortCircuitInstructionModel shortCircuitInstruction = (ShortCircuitInstructionModel) instr;
                        /*
                         * NB: Short circuit operations can evaluate to an operand or to the boolean
                         * conversion of an operand. The stack is different in either case.
                         */
                        b.declaration(context.getDeclaredType(Object.class), "booleanResult", getFrameObject("sp - 1"));

                        b.startIf().string("booleanResult", shortCircuitInstruction.continueWhen ? " != " : " == ", "Boolean.TRUE").end().startBlock();
                        if (shortCircuitInstruction.returnConvertedValue) {
                            // Stack: [..., convertedValue]
                            // leave convertedValue on the top of stack
                        } else {
                            // Stack: [..., value, convertedValue]
                            // pop convertedValue
                            b.statement(clearFrame("sp - 1"));
                            b.statement("sp -= 1");
                        }
                        b.statement("bci = " + readBc("bci + 1"));
                        b.statement("continue loop");
                        b.end().startElseBlock();
                        if (shortCircuitInstruction.returnConvertedValue) {
                            // Stack: [..., convertedValue]
                            // clear convertedValue
                            b.statement(clearFrame("sp - 1"));
                            b.statement("sp -= 1");
                        } else {
                            // Stack: [..., value, convertedValue]
                            // clear convertedValue and value
                            b.statement(clearFrame("sp - 1"));
                            b.statement(clearFrame("sp - 2"));
                            b.statement("sp -= 2");
                        }
                        b.statement("bci += " + instr.getInstructionLength());
                        b.statement("continue loop");
                        b.end();
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

            DeclaredType abstractTruffleException = types.AbstractTruffleException;
            b.startCatchBlock(context.getDeclaredType("java.lang.Throwable"), "throwable");

            storeBciInFrameIfNecessary(b);
            /*
             * We can handle throwable if it's an AbstractTruffleException or
             * interceptInternalException converts it to one.
             */
            b.declaration(abstractTruffleException, "ex");
            b.startIf().startGroup().string("throwable instanceof ").type(abstractTruffleException).string(" ate").end(2).startBlock();
            b.startAssign("ex").string("ate").end();
            b.end().startElseBlock();
            b.startTryBlock(); // nested try
            b.tree(createTransferToInterpreterAndInvalidate("$this"));
            b.startThrow().string("sneakyThrow($this.interceptInternalException(throwable, bci))").end();
            b.end().startCatchBlock(abstractTruffleException, "ate");
            b.startAssign("ex").string("ate").end();
            b.end(); // nested try
            b.end(); // else

            b.statement("ex = $this.interceptTruffleException(ex, " + localFrame() + ", bci)");

            b.statement("int[] handlers = $this.handlers");
            b.startFor().string("int idx = 0; idx < handlers.length; idx += 5").end().startBlock();

            b.startIf().string("handlers[idx] > bci").end().startBlock().statement("continue").end();
            b.startIf().string("handlers[idx + 1] <= bci").end().startBlock().statement("continue").end();
            b.statement("bci = handlers[idx + 2]");
            b.statement("int handlerSp = handlers[idx + 3] + $this.numLocals");
            b.statement("assert sp >= handlerSp");
            b.startWhile().string("sp > handlerSp").end().startBlock();
            b.statement(clearFrame("--sp"));
            b.end();

            b.statement(setFrameObject(localFrame(), "handlers[idx + 4]", "ex"));

            b.statement("continue loop");

            b.end(); // for

            /**
             * NB: Reporting here ensures loop counts are reported before a guest-language exception
             * bubbles up. Loop counts may be lost when host exceptions are thrown (a compromise to
             * avoid complicating the generated code too much).
             */
            emitReportLoopCount(b, CodeTreeBuilder.singleString("loopCounter.value > 0"), false);
            b.statement("throw ex");

            b.end(); // catch

            b.end(); // while (true)

            if (model.enableTracing) {
                b.end().startFinallyBlock();
                b.statement("tracer.endRoot($this)");
                b.end();
            }

            return results;
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
            b.string("$this");
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
            String helperName = customInstructionHelperName(instr);

            CodeExecutableElement helper = new CodeExecutableElement(Set.of(PRIVATE, STATIC, FINAL), context.getType(void.class), helperName);

            helper.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));
            if (model.enableYield) {
                helper.getParameters().add(new CodeVariableElement(types.VirtualFrame, "localFrame"));
            }

            if (!tier.isUncached) {
                helper.addParameter(new CodeVariableElement(new ArrayCodeTypeMirror(types.Node), "cachedNodes"));
                if (model.enableTracing) {
                    helper.addParameter(new CodeVariableElement(types.ExecutionTracer, "tracer"));
                }
            }

            /**
             * These additional parameters mirror the parameters declared in
             * {@link OperationNodeGeneratorPlugs#additionalArguments()}. When one is updated, the
             * other should be kept in sync.
             */
            // we forward parameters with the same name when we call the helper, so save them here.
            List<CodeVariableElement> extraParams = new ArrayList<>();
            extraParams.addAll(List.of(
                            new CodeVariableElement(operationNodeGen.asType(), "$this"),
                            new CodeVariableElement(context.getType(short[].class), "bc"),
                            new CodeVariableElement(context.getType(int.class), "bci"),
                            new CodeVariableElement(context.getType(int.class), "sp")));

            helper.getParameters().addAll(extraParams);

            TypeMirror cachedType = new GeneratedTypeMirror("", cachedDataClassName(instr));
            boolean isVoid = instr.signature.isVoid;

            CodeTreeBuilder b = helper.createBuilder();

            // Since an instruction produces at most one value, stackEffect is at most 1.
            int stackEffect = (isVoid ? 0 : 1) - instr.signature.valueCount;

            if (customInstructionMayReadBci(instr)) {
                storeBciInFrameIfNecessary(b);
            }

            if (!tier.isUncached) {
                // If not in the uncached interpreter, we need to retrieve the node for the call.
                InstructionImmediate imm = instr.getImmediate(ImmediateKind.NODE);
                String nodeIndex = readBc("bci + " + imm.offset);
                CodeTree readNode = CodeTreeBuilder.createBuilder().string(readNode(cachedType, nodeIndex)).build();
                b.declaration(cachedType, "node", readNode);

                if (model.enableTracing) {
                    b.startBlock();

                    b.startAssign("var specInfo").startStaticCall(types.Introspection, "getSpecializations");
                    b.string("node");
                    b.end(2);

                    b.startStatement().startCall("tracer.traceActiveSpecializations");
                    b.string("bci");
                    b.string(instr.id);
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

            if (isVoid) {
                b.startStatement();
            } else {
                b.startAssign("Object result");
            }

            if (tier.isUncached) {
                b.staticReference(cachedType, "UNCACHED").startCall(".executeUncached");
            } else if (isVoid) {
                b.string("node").startCall(".executeVoid");
            } else {
                b.string("node").startCall(".executeObject");
            }

            // If we support yield, the frame forwarded to specializations should be the local frame
            // and not the stack frame.
            b.string(localFrame());

            // The tier 0 version takes all of its parameters. Other versions compute them.
            if (tier.isUncached) {
                for (int i = 0; i < instr.signature.valueCount; i++) {
                    TypeMirror targetType = instr.signature.getParameterType(i);
                    b.startGroup();
                    if (!ElementUtils.isObject(targetType)) {
                        b.cast(targetType);
                    }
                    b.string(getFrameObject("sp - " + (instr.signature.valueCount - i)));
                    b.end();
                }

                for (InstructionImmediate immediate : instr.getImmediates(ImmediateKind.LOCAL_SETTER)) {
                    b.startStaticCall(types.LocalSetter, "get");
                    b.string(readBc("bci + " + immediate.offset));
                    b.end();
                }

                for (InstructionImmediate immediate : instr.getImmediates(ImmediateKind.LOCAL_SETTER_RANGE_START)) {
                    b.startStaticCall(types.LocalSetterRange, "get");
                    b.string(readBc("bci + " + immediate.offset)); // start
                    b.string(readBc("bci + " + (immediate.offset + 1))); // length
                    b.end();
                }
            }

            if (model.enableYield) {
                b.string("frame"); // passed for $stackFrame
            }
            b.variables(extraParams);
            b.end(2);

            // Update the stack.
            if (!isVoid) {
                if (stackEffect == 1) {
                    b.statement(setFrameObject("sp", "result"));
                } else {
                    b.statement(setFrameObject("sp - " + (1 - stackEffect), "result"));
                }
            }
            for (int i = stackEffect; i < 0; i++) {
                // When stackEffect is negative, values should be cleared from the top of the stack.
                b.statement(clearFrame("sp - " + -i));
            }

            // In continueAt, call the helper and adjust sp.
            continueAtBuilder.startStatement().startCall(helperName);
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
            if (tier.isUncached || model.storeBciInFrame) {
                b.statement("ACCESS.setInt(" + localFrame() + ", " + BCI_IDX + ", bci)");
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

        private String customInstructionHelperName(InstructionModel instr) {
            String withoutPrefix = switch (instr.kind) {
                case CUSTOM -> {
                    assert instr.name.startsWith("c.");
                    yield instr.name.substring(2);
                }
                case CUSTOM_SHORT_CIRCUIT -> {
                    assert instr.name.startsWith("sc.");
                    yield instr.name.substring(3);
                }
                default -> throw new AssertionError("Unexpected instruction " + instr + " with kind " + instr.kind.name());
            };

            return "do" + Arrays.stream(withoutPrefix.split("\\.")).map(part -> firstLetterUpperCase(part)).collect(Collectors.joining());
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
            bytecodeLocalImpl.setEnclosingElement(operationNodeGen);

            bytecodeLocalImpl.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), context.getType(int.class), "index"));

            bytecodeLocalImpl.add(createConstructorUsingFields(Set.of(), bytecodeLocalImpl, null));

            return bytecodeLocalImpl;
        }
    }

    class BytecodeLabelImplFactory {
        private CodeTypeElement create() {
            bytecodeLabelImpl.setSuperClass(generic(types.BytecodeLabel, model.templateType.asType()));
            bytecodeLabelImpl.setEnclosingElement(operationNodeGen);

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
            b.startReturn().string("bci != ").staticReference(operationBuilderType, BuilderFactory.UNINIT).end();
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
            continuationRootNodeImpl.setEnclosingElement(operationNodeGen);
            continuationRootNodeImpl.setSuperClass(types.RootNode);
            continuationRootNodeImpl.getImplements().add(types.ContinuationRootNode);

            continuationRootNodeImpl.add(new CodeVariableElement(Set.of(FINAL), operationNodeGen.asType(), "root"));
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
            b.declaration(types.Frame, "localFrame", cast(types.Frame, getFrameObject(COROUTINE_FRAME_IDX)));
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

            continuationLocationImpl.setEnclosingElement(operationNodeGen);
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

            return continuationLocationImpl;
        }

        private CodeExecutableElement createValidateArgument() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), context.getType(void.class), "validateArgument");
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "value"));
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("assert value >= 0");
            b.startIf().string("(1 << 16) <= value").end().startBlock();
            buildThrowIllegalStateException(b, "\"ContinuationLocation field exceeded maximum size that could be encoded.\"");
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
    }

    private static final Set<String> BOXING_ELIMINATED_EXECUTE_NAMES = Set.of("executeBoolean", "executeLong", "executeInt", "executeByte", "executeDouble", "executeFloat");
    private static final Set<String> EXECUTE_NAMES = Set.of("executeBoolean", "executeLong", "executeInt", "executeByte", "executeDouble", "executeFloat", "executeObject");

    /**
     * Custom instructions are generated from Operations and OperationProxies. During parsing we
     * convert these definitions into Nodes for which {@link FlatNodeGenFactory} understands how to
     * generate specialization code. We clean up the result (removing unnecessary fields/methods,
     * fixing up types, etc.) here.
     */
    private class CustomInstructionPostProcessor {

        @SuppressWarnings({"unchecked", "rawtypes"})
        private void process(CodeTypeElement el) {
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

            for (ExecutableElement met : ElementFilter.methodsIn(el.getEnclosedElements())) {
                if (BOXING_ELIMINATED_EXECUTE_NAMES.contains(met.getSimpleName().toString())) {
                    if (!met.getThrownTypes().contains(types.UnexpectedResultException)) {
                        ((List) met.getThrownTypes()).add(types.UnexpectedResultException);
                    }
                }
            }

            for (CodeTypeElement type : (List<CodeTypeElement>) (List<?>) ElementFilter.typesIn(el.getEnclosedElements())) {
                if (type.getSimpleName() == Uncached_Name) {
                    type.setSuperClass(types.Node);
                }
            }

            if (OperationsNodeFactory.this.model.enableUncachedInterpreter) {
                // We inject a method to ensure the uncached entrypoint is statically known. We do
                // not need this method on the base class.
                for (ExecutableElement met : ElementFilter.methodsIn(el.getEnclosedElements())) {
                    if (met.getSimpleName().toString().equals("executeUncached")) {
                        el.getEnclosedElements().remove(met);
                    }
                }
                // We do not need any other execute methods on the Uncached class.
                for (TypeElement cls : ElementFilter.typesIn(el.getEnclosedElements())) {
                    if (cls.getSimpleName() == Uncached_Name) {
                        for (ExecutableElement met : ElementFilter.methodsIn(cls.getEnclosedElements())) {
                            if (EXECUTE_NAMES.contains(met.getSimpleName().toString())) {
                                cls.getEnclosedElements().remove(met);
                            }
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

    private CodeTree createTransferToInterpreterAndInvalidate(String root) {

        if (model.templateType.getSimpleName().toString().equals("BoxingOperations")) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
            b.statement(root + ".transferToInterpreterAndInvalidate()");
            return b.build();
        } else {
            return GeneratorUtils.createTransferToInterpreterAndInvalidate();
        }
    }

    private void buildFence(CodeTreeBuilder b) {
        b.startStatement().startStaticCall(context.getType(VarHandle.class), "storeStoreFence").end(2);
    }

    private void buildThrowIllegalStateException(CodeTreeBuilder b, String reasonCode) {
        buildThrow(b, IllegalStateException.class, reasonCode);
    }

    private void buildThrow(CodeTreeBuilder b, Class<? extends Throwable> exceptionClass, String reasonCode) {
        b.startThrow().startNew(context.getType(exceptionClass));
        if (reasonCode != null) {
            b.string(reasonCode);
        }
        b.end(2);
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

    private CodeTree createInstructionConstant(InstructionModel instr) {
        return CodeTreeBuilder.createBuilder().staticReference(instructionsElement.asType(), instr.getConstantName()).build();
    }

    private CodeTree createOperationConstant(OperationModel op) {
        return CodeTreeBuilder.createBuilder().staticReference(operationsElement.asType(), op.getConstantName()).build();
    }

    private CodeTree castParser(CodeExecutableElement ex, String parser) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.startParantheses();
        b.cast(generic(types.BytecodeParser, builder.asType()));
        b.string(parser);
        b.end();
        mergeSuppressWarnings(ex, "unchecked");
        return b.build();
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

    private static String readProfile(String index) {
        return String.format("branchProfiles == null ? 0 : ACCESS.intArrayRead(branchProfiles, %s)", index);
    }

    private static String readNode(TypeMirror expectedType, String index) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.startCall("ACCESS.cast");
        b.startCall("ACCESS.objectArrayRead");
        b.string("cachedNodes");
        b.string(index);
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

    private static String setFrameObject(String index, String value) {
        return setFrameObject("frame", index, value);
    }

    private static String setFrameObject(String frame, String index, String value) {
        return String.format("ACCESS.setObject(%s, %s, %s)", frame, index, value);
    }

    private static String clearFrame(String index) {
        return String.format("ACCESS.clear(frame, %s)", index);
    }

    private static String copyFrameSlot(String src, String dst) {
        return String.format("ACCESS.copy(frame, %s, %s)", src, dst);
    }

    private static String copyFrameTo(String srcFrame, String srcOffset, String dstFrame, String dstOffset, String length) {
        return String.format("ACCESS.copyTo(%s, %s, %s, %s, %s)", srcFrame, srcOffset, dstFrame, dstOffset, length);
    }

    private static String cachedDataClassName(InstructionModel instr) {
        return instr.getInternalName() + "Gen";
    }

    private static String childString(int numChildren) {
        return numChildren + ((numChildren == 1) ? " child" : " children");
    }

    /**
     * User code directly references some generated types and methods, like builder methods. When
     * there is an error in the model, this factory generates stubs for the user-accessible names to
     * prevent the compiler for emitting many unhelpful error messages about unknown types/methods.
     */
    public static final class ErrorFactory {
        private final ProcessorContext context = ProcessorContext.getInstance();
        private final TruffleTypes types = context.getTypes();

        private final OperationsModel model;
        private final CodeTypeElement operationNodeGen;

        private final CodeTypeElement builder = new CodeTypeElement(Set.of(PUBLIC, STATIC, FINAL), ElementKind.CLASS, null, "Builder");
        private final DeclaredType operationBuilderType = new GeneratedTypeMirror("", builder.getSimpleName().toString(), builder.asType());
        private final TypeMirror parserType = generic(types.BytecodeParser, operationBuilderType);

        public ErrorFactory(OperationsModel model) {
            assert model.hasErrors();
            this.model = model;
            this.operationNodeGen = GeneratorUtils.createClass(model.templateType, null, Set.of(PUBLIC, FINAL), model.getName(), model.templateType.asType());
        }

        public CodeTypeElement create() {
            operationNodeGen.add(createExecute());
            operationNodeGen.add(createConstructor());
            operationNodeGen.add(createCreate());
            if (model.enableSerialization) {
                operationNodeGen.add(createSerialize());
                operationNodeGen.add(createDeserialize());
            }

            operationNodeGen.add(new BuilderFactory().create());
            return operationNodeGen;
        }

        private CodeExecutableElement createExecute() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.RootNode, "execute");
            CodeTreeBuilder b = ex.createBuilder();
            emitThrowNotImplemented(b);
            return ex;
        }

        private CodeExecutableElement createConstructor() {
            CodeExecutableElement ctor = new CodeExecutableElement(Set.of(PRIVATE), null, operationNodeGen.getSimpleName().toString());
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
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC, STATIC), generic(types.BytecodeNodes, model.templateType.asType()), "create");
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
                            generic(types.BytecodeNodes, model.getTemplateType().asType()), "deserialize");
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
                builder.setEnclosingElement(operationNodeGen);
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
                for (CodeVariableElement param : operation.getOperationArguments()) {
                    ex.addParameter(param);
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
                for (CodeVariableElement param : operation.getOperationArguments()) {
                    ex.addParameter(param);
                }
                return createMethodStub(ex);
            }
        }
    }
}
