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

import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.addSuppressWarnings;
import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.createConstructorUsingFields;
import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.createNeverPartOfCompilation;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.boxType;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.firstLetterUpperCase;
import static com.oracle.truffle.dsl.processor.operations.generator.ElementHelpers.addField;
import static com.oracle.truffle.dsl.processor.operations.generator.ElementHelpers.arrayOf;
import static com.oracle.truffle.dsl.processor.operations.generator.ElementHelpers.generic;
import static com.oracle.truffle.dsl.processor.operations.generator.ElementHelpers.type;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

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

import sun.misc.Unsafe;

import com.oracle.truffle.dsl.processor.operations.model.OperationsModel;

public class OperationsNodeFactory implements ElementHelpers {
    private static final Name Uncached_Name = CodeNames.of("Uncached");

    private final ProcessorContext context = ProcessorContext.getInstance();
    private final TruffleTypes types = context.getTypes();
    private final OperationsModel model;

    // All of the following CodeTypeElements represent classes generated by this factory. Some are
    // conditionally generated depending on the model, so they get initialized in the constructor.
    // After initialization, the code for each class must still be generated; this is done by the
    // XYZFactory classes.

    // The top-level class that subclasses the node annotated with @GenerateOperations.
    // All of the definitions that follow are nested inside of this class.
    private final CodeTypeElement operationNodeGen;

    // The builder class invoked by the language parser to generate the bytecode.
    private final CodeTypeElement builder = new CodeTypeElement(Set.of(PUBLIC, STATIC, FINAL), ElementKind.CLASS, null, "Builder");
    private final DeclaredType operationBuilderType = new GeneratedTypeMirror("", builder.getSimpleName().toString(), builder.asType());
    private final TypeMirror parserType = generic(types.OperationParser, operationBuilderType);

    // Implementations of public classes that Truffle interpreters interact with.
    private final CodeTypeElement operationNodesImpl = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "OperationNodesImpl");
    private final CodeTypeElement operationLocalImpl = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "OperationLocalImpl");
    private final CodeTypeElement operationLabelImpl = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "OperationLabelImpl");

    // Helper classes that map instructions/operations to constant integral values.
    private final CodeTypeElement instructionsElement = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "Instructions");
    private final CodeTypeElement operationsElement = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "Operations");

    // The interpreters store additional instruction data (e.g. branches, inline caches) in an
    // Object[] indexed by bci.
    // The following classes represent default data objects that can be stored in the array.

    // Interface representing data objects that can have a specified boxing state.
    private final CodeTypeElement boxableInterface = new CodeTypeElement(Set.of(PRIVATE), ElementKind.INTERFACE, null, "BoxableInterface");

    // Root node and ContinuationLocal classes to support yield.
    private final CodeTypeElement continuationRoot;
    private final CodeTypeElement continuationLocationImpl;

    // Singleton field for an empty array.
    private final CodeVariableElement emptyObjectArray;

    public OperationsNodeFactory(OperationsModel model) {
        this.model = model;
        operationNodeGen = GeneratorUtils.createClass(model.templateType, null, Set.of(PUBLIC, FINAL), model.templateType.getSimpleName() + "Gen", model.templateType.asType());
        emptyObjectArray = addField(operationNodeGen, Set.of(PRIVATE, STATIC, FINAL), Object[].class, "EMPTY_ARRAY", "new Object[0]");

        if (model.enableYield) {
            continuationRoot = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "ContinuationRoot");
            continuationLocationImpl = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "ContinuationLocationImpl");
        } else {
            continuationRoot = null;
            continuationLocationImpl = null;
        }

    }

    public CodeTypeElement create() {

        // Print a summary of the model in a docstring at the start.
        operationNodeGen.createDocBuilder().startDoc().lines(model.infodump()).end();

        // Define the interpreter implementations.
        if (model.enableBaselineInterpreter) {
            operationNodeGen.add(new ContinueAtFactory(InterpreterTier.TIER0).create());
        }
        operationNodeGen.addAll(createInterpreterTiers());
        operationNodeGen.add(createCurrentTierField());
        operationNodeGen.add(new ContinueAtFactory(InterpreterTier.TIER1).create());
        operationNodeGen.add(new ContinueAtFactory(InterpreterTier.INSTRUMENTED).create());

        // Define the builder class.
        operationNodeGen.add(new BuilderFactory().create());

        // Define implementations for the public classes that Truffle interpreters interact with.
        operationNodeGen.add(new OperationNodesImplFactory().create());
        operationNodeGen.add(new OperationLocalImplFactory().create());
        operationNodeGen.add(new OperationLabelImplFactory().create());

        // Define helper classes containing the constants for instructions and operations.
        operationNodeGen.add(new InstructionConstantsFactory().create());
        operationNodeGen.add(new OperationsConstantsFactory().create());

        // Define the classes that model instruction data (e.g., cache data, continuation data).
        operationNodeGen.add(new BoxableInterfaceFactory().create());

        // Define the classes that implement continuations (yield).
        if (model.enableYield) {
            operationNodeGen.add(new ContinuationRootFactory().create());
            operationNodeGen.add(new ContinuationLocationImplFactory().create());
        }

        // Define the generated node's constructor.
        operationNodeGen.add(createConstructor());

        // Define the execute method.
        operationNodeGen.add(createExecute());

        // Define a continueAt method.
        // This method delegates to the current tier's continueAt, handling the case where
        // the tier changes.
        operationNodeGen.add(createContinueAt());

        // Define the members required to support OSR.
        operationNodeGen.addAll(new OSRMembersFactory().create());

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

        // Define an Unsafe singleton, if Unsafe is allowed.
        if (model.allowUnsafe) {
            operationNodeGen.addAll(GeneratorUtils.createUnsafeSingleton());
        }

        // Generate a {@link @TracingConfiguration} annotation, if tracing is enabled.
        if (model.enableTracing) {
            operationNodeGen.addAnnotationMirror(createTracingMetadata());
        }

        // Define helpers used by the continueAt methods to access arrays.
        operationNodeGen.add(createReadShortInBounds());
        operationNodeGen.add(createReadNodeInBounds());
        operationNodeGen.add(createReadObjectInBounds());

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
        operationNodeGen.add(compFinal(new CodeVariableElement(Set.of(PRIVATE), operationNodesImpl.asType(), "nodes")));
        operationNodeGen.add(compFinal(1, new CodeVariableElement(Set.of(PRIVATE), context.getType(short[].class), "bc")));
        operationNodeGen.add(compFinal(1, new CodeVariableElement(Set.of(PRIVATE), context.getType(Object[].class), "constants")));
        operationNodeGen.add(compFinal(1, new CodeVariableElement(Set.of(PRIVATE), new ArrayCodeTypeMirror(types.Node), "cachedNodes")));
        operationNodeGen.add(compFinal(1, new CodeVariableElement(Set.of(PRIVATE), context.getType(int[].class), "handlers")));
        operationNodeGen.add(compFinal(1, new CodeVariableElement(Set.of(PRIVATE), context.getType(int[].class), "sourceInfo")));
        operationNodeGen.add(compFinal(new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "numLocals")));
        operationNodeGen.add(compFinal(new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "numNodes")));
        operationNodeGen.add(compFinal(new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "buildIndex")));
        if (model.hasBoxingElimination()) {
            operationNodeGen.add(compFinal(1, new CodeVariableElement(Set.of(PRIVATE), context.getType(byte[].class), "localBoxingState")));
        }
        if (model.enableBaselineInterpreter) {
            operationNodeGen.add(new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "uncachedExecuteCount")).createInitBuilder().string("16");
        }
        if (model.enableTracing) {
            operationNodeGen.add(compFinal(1, new CodeVariableElement(Set.of(PRIVATE), context.getType(boolean[].class), "basicBlockBoundary")));
        }

        // Define helpers for variadic accesses.
        operationNodeGen.add(createReadVariadic());
        operationNodeGen.add(createMergeVariadic());

        // Define helpers for boxing-eliminated accesses.
        if (model.hasBoxingElimination()) {
            operationNodeGen.add(createDoPopObject());
            for (TypeMirror type : model.boxingEliminatedTypes) {
                operationNodeGen.add(createDoPopPrimitive(type));
            }
        }

        // Define the generated Node classes for custom instructions.
        StaticConstants consts = new StaticConstants();
        for (InstructionModel instr : model.getInstructions()) {
            if (instr.nodeData == null) {
                continue;
            }

            NodeConstants nodeConsts = new NodeConstants();
            OperationNodeGeneratorPlugs plugs = new OperationNodeGeneratorPlugs(context, operationNodeGen.asType(), instr);
            FlatNodeGenFactory factory = new FlatNodeGenFactory(context, GeneratorMode.DEFAULT, instr.nodeData, consts, nodeConsts, plugs);

            CodeTypeElement el = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, cachedDataClassName(instr));
            el.setSuperClass(types.Node);
            factory.create(el);
            new CustomInstructionPostProcessor().process(el, instr);

            nodeConsts.prependToClass(el);
            operationNodeGen.add(el);
        }
        consts.addElementsTo(operationNodeGen);

        // Define helpers for obtaining and initializing the cached nodes.
        operationNodeGen.add(createGetCachedNodes());
        operationNodeGen.add(createInitializeCachedNodes());
        operationNodeGen.add(createCreateCachedNodes());

        // TODO: this method is here for debugging and should probably be omitted before we release
        operationNodeGen.add(createDumpBytecode());

        return operationNodeGen;
    }

    private CodeExecutableElement createGetSourceSection() {
        CodeExecutableElement ex = GeneratorUtils.override(types.Node, "getSourceSection");
        CodeTreeBuilder b = ex.createBuilder();

        b.startIf().string("sourceInfo == null || sourceInfo.length == 0").end().startBlock();
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
        CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.OperationRootNode, "getSourceSectionAtBci");
        ex.renameArguments("bci");
        CodeTreeBuilder b = ex.createBuilder();

        b.startIf().string("sourceInfo == null || sourceInfo.length == 0").end().startBlock();
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

        b.declaration(operationNodeGen.asType(), "clone");
        b.startAssign("clone");
        b.cast(operationNodeGen.asType(), "this.copy()");
        b.end();

        // The base copy method performs a shallow copy of all fields.
        // Some fields should be manually reinitialized to default values.
        b.statement("clone.cachedNodes = null"); // cachedNodes will be set on first execution

        if (model.enableBaselineInterpreter) {
            b.statement("clone.uncachedExecuteCount = 16");
            b.statement("clone.currentTier = " + InterpreterTier.TIER0.name());
        } else {
            b.statement("clone.currentTier = " + InterpreterTier.TIER1.name());
        }

        b.startReturn().string("clone").end();

        return ex;
    }

    private enum InterpreterTier {
        TIER0("Tier0", true, false),
        TIER1("Tier1", false, false),
        INSTRUMENTED("Instrumented", false, true);

        final String friendlyName;
        final boolean isUncached;
        final boolean isInstrumented;

        private InterpreterTier(String friendlyName, boolean isUncached, boolean isInstrumented) {
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
        if (model.enableBaselineInterpreter) {
            tiers.add(InterpreterTier.TIER0);
        }
        tiers.add(InterpreterTier.TIER1);
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

        if (model.enableBaselineInterpreter) {
            fld.createInitBuilder().string(InterpreterTier.TIER0.name());
        } else {
            fld.createInitBuilder().string(InterpreterTier.TIER1.name());
        }

        return fld;
    }

    private CodeAnnotationMirror createTracingMetadata() {
        CodeAnnotationMirror mir = new CodeAnnotationMirror(types.OperationTracingMetadata);

        mir.setElementValue("decisionsFile", new CodeAnnotationValue(model.decisionsFilePath));

        List<CodeAnnotationValue> instructionNames = model.getInstructions().stream().map(instr -> new CodeAnnotationValue(instr.name)).collect(Collectors.toList());
        // instruction opcodes start at 1. Insert a placeholder element to simplify indexing.
        instructionNames.add(0, new CodeAnnotationValue("NO_INSTRUCTION"));
        mir.setElementValue("instructionNames", new CodeAnnotationValue(instructionNames));

        List<CodeAnnotationValue> specializationNames = model.getInstructions().stream().filter(instr -> instr.isCustomInstruction()).map(instr -> {
            CodeAnnotationMirror instructionSpecializationNames = new CodeAnnotationMirror(types.OperationTracingMetadata_SpecializationNames);

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
        ex.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));
        if (model.enableYield) {
            ex.addParameter(new CodeVariableElement(types.VirtualFrame, "generatorFrame"));
        }
        ex.addParameter(new CodeVariableElement(context.getType(int.class), "startState"));

        CodeTreeBuilder b = ex.createBuilder();

        // todo: only generate executeProlog/Epilog calls and the try/finally if they are overridden

        b.statement("Throwable throwable = null");
        b.statement("Object returnValue = null");

        b.statement("this.executeProlog(frame)");

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
            }
            b.startAssign("state").startCall(tier.interpreterClassName() + ".continueAt");
            b.string("this, frame");
            if (model.enableYield) {
                b.string("generatorFrame");
            }
            b.string("bc");
            b.string("constants");
            if (!tier.isUncached) {
                b.string("cachedNodes");
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

        b.startAssign("returnValue").string("frame.getObject((state >> 16) & 0xffff)").end();
        b.statement("return returnValue");

        b.end().startCatchBlock(context.getType(Throwable.class), "th");
        b.statement("throw sneakyThrow(throwable = th)");
        b.end().startFinallyBlock();
        b.statement("this.executeEpilog(frame, returnValue, throwable)");
        b.end();

        return ex;
    }

    private CodeExecutableElement createSneakyThrow() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), context.getType(RuntimeException.class), "sneakyThrow");

        TypeMirror throwable = context.getType(Throwable.class);
        CodeVariableElement param = new CodeVariableElement(throwable, "e");
        ex.addParameter(param);

        CodeTypeParameterElement E = new CodeTypeParameterElement(CodeNames.of("E"), throwable);
        ex.getTypeParameters().add(E);
        ex.addThrownType(E.asType());

        addSuppressWarnings(context, ex, "unchecked");
        CodeTreeBuilder b = ex.createBuilder();
        b.startThrow();
        b.cast(E.asType()).variable(param);
        b.end();

        return ex;
    }

    private CodeExecutableElement createCreate() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC, STATIC), generic(types.OperationNodes, model.templateType.asType()), "create");
        ex.addParameter(new CodeVariableElement(types.OperationConfig, "config"));
        ex.addParameter(new CodeVariableElement(generic(types.OperationParser, builder.asType()), "generator"));

        CodeTreeBuilder b = ex.getBuilder();

        b.declaration("OperationNodesImpl", "nodes", "new OperationNodesImpl(generator)");
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

        addSuppressWarnings(context, method, "unchecked");

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
        method.addParameter(new CodeVariableElement(types.OperationConfig, "config"));
        method.addParameter(new CodeVariableElement(context.getType(DataOutput.class), "buffer"));
        method.addParameter(new CodeVariableElement(types.OperationSerializer, "callback"));
        method.addParameter(new CodeVariableElement(parserType, "parser"));
        method.addThrownType(context.getType(IOException.class));

        CodeTreeBuilder init = CodeTreeBuilder.createBuilder();
        init.startNew(operationBuilderType);
        init.startGroup();
        init.cast(operationNodesImpl.asType());
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
                        generic(types.OperationNodes, model.getTemplateType().asType()), "deserialize");

        method.addParameter(new CodeVariableElement(types.TruffleLanguage, "language"));
        method.addParameter(new CodeVariableElement(types.OperationConfig, "config"));
        method.addParameter(new CodeVariableElement(generic(Supplier.class, DataInput.class), "input"));
        method.addParameter(new CodeVariableElement(types.OperationDeserializer, "callback"));
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
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), types.OperationIntrospection, "getIntrospectionData");
        CodeTreeBuilder b = ex.createBuilder();

        b.statement("List<Object> instructions = new ArrayList<>()");

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
                case BRANCH_FALSE:
                    buildIntrospectionArgument(b, "BRANCH_OFFSET", readBc("bci + 1"));
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

        b.startReturn().startStaticCall(types.OperationIntrospection_Provider, "create");
        b.string("new Object[]{0, instructions.toArray(), exHandlersInfo, null}");
        b.end(2);

        return ex;
    }

    private void buildIntrospectionArgument(CodeTreeBuilder b, String kind, String content) {
        DeclaredType argumentKindType = context.getDeclaredType("com.oracle.truffle.api.operation.introspection.Argument.ArgumentKind");

        b.startNewArray(arrayOf(context.getType(Object.class)), null);
        b.staticReference(argumentKindType, kind);
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
        b.statement("result[i] = frame.getObject(index)");
        b.statement("frame.clear(index)");
        b.end();

        b.statement("return result");

        return ex;
    }

    private CodeExecutableElement createMergeVariadic() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), context.getType(Object[].class), "mergeVariadic");

// ex.addParameter(new CodeVariableElement(type(Object[].class), "array0"));
// ex.addParameter(new CodeVariableElement(type(Object[].class), "array1"));
//
// CodeTreeBuilder b = ex.createBuilder();
// b.startAssert().string("array0.length >= ").string(model.popVariadicInstruction.length -
// 1).end();
// b.startAssert().string("array1.length > 0").end();
//
// b.statement("Object[] newArray = new Object[array0.length + array1.length]");
// b.statement("System.arraycopy(array0, 0, newArray, 0, array0.length)");
// b.statement("System.arraycopy(array1, 0, newArray, array0.length, array1.length)");
//
// b.startReturn().string("newArray").end();
//
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

    private CodeExecutableElement createDoPopObject() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), context.getType(Object.class), "doPopObject");
        ex.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));
        ex.addParameter(new CodeVariableElement(operationNodeGen.asType(), "$this"));
        ex.addParameter(new CodeVariableElement(context.getType(int.class), "slot"));
        ex.addParameter(new CodeVariableElement(context.getType(int.class), "boxing"));
        ex.addParameter(new CodeVariableElement(context.getType(Object[].class), "objs"));

        CodeTreeBuilder b = ex.createBuilder();

        b.startIf().string("(boxing & 0xffff0000) == 0xffff0000 || frame.isObject(slot)").end().startBlock(); // {
        b.startReturn().string("frame.getObject(slot)").end();
        b.end(); // }

        b.tree(createTransferToInterpreterAndInvalidate("$this"));
        b.statement("((BoxableInterface) objs[boxing & 0xffff]).setBoxing((boxing >> 16) & 0xffff, (byte) -1)");
        b.startReturn().string("frame.getValue(slot)").end();

        return ex;
    }

    private CodeExecutableElement createDoPopPrimitive(TypeMirror resultType) {
        String typeName = firstLetterUpperCase(resultType.toString());
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), resultType, "doPopPrimitive" + typeName);
        ex.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));
        ex.addParameter(new CodeVariableElement(operationNodeGen.asType(), "$this"));
        ex.addParameter(new CodeVariableElement(context.getType(int.class), "slot"));
        ex.addParameter(new CodeVariableElement(context.getType(int.class), "boxing"));
        ex.addParameter(new CodeVariableElement(context.getType(Object[].class), "objs"));

        ex.addThrownType(types.UnexpectedResultException);

        CodeTreeBuilder b = ex.createBuilder();

        b.startIf().string("(boxing & 0xffff0000) == 0xffff0000").end().startBlock(); // {
        b.statement("Object result = frame.getObject(slot)");

        b.startIf().string("result").instanceOf(boxType(resultType)).end().startBlock(); // {
        b.startReturn().cast(resultType).string("result").end();
        b.end().startElseBlock(); // } {
        b.tree(createTransferToInterpreterAndInvalidate("$this"));
// b.statement("System.err.printf(\" [**] expected " + resultType + " but got %s %s [no BE]%n\",
// result == null ? \"null\" : result.getClass(), result)");
        b.startThrow().startNew(types.UnexpectedResultException).string("result").end(2);
        b.end(); // }

        b.end().startElseBlock(); // } {

        b.startIf().string("frame.is" + typeName + "(slot)").end().startBlock();
        b.startReturn().string("frame.get" + typeName + "(slot)").end();
        b.end().startElseBlock();

        b.tree(createTransferToInterpreterAndInvalidate("$this"));

        b.statement("Object result = frame.getValue(slot)");

        b.startStatement();
        b.string("((BoxableInterface) objs[boxing & 0xffff]).setBoxing((boxing >> 16) & 0xffff, (byte) ").tree(boxingTypeToInt(resultType)).string(")");
        b.end();

// b.statement("System.err.printf(\" [**] expected " + resultType +
// " but got %s %s (%08x %s) [BE faul]%n\", result == null ? \"null\" : result.getClass(), result,
// boxing, objs[boxing & 0xffff].getClass())");

        b.startIf().string("result").instanceOf(boxType(resultType)).end().startBlock(); // {
        b.startReturn().cast(resultType).string("result").end();
        b.end().startElseBlock(); // } {
        b.startThrow().startNew(types.UnexpectedResultException).string("result").end(2);
        b.end();

        b.end();

        b.end();

        return ex;
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

        ex.addParameter(new CodeVariableElement(context.getType(int.class), "newTier"));

        CodeTreeBuilder b = ex.createBuilder();

        b.statement("int currentTier = this.currentTier");

        b.startIf().string("newTier == currentTier").end().startBlock();
        b.returnStatement();
        b.end();

        b.startIf().string("newTier == TIER1 && currentTier == INSTRUMENTED").end().startBlock();
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

        Map<Boolean, List<InstructionModel>> instructionsGroupedByIsCustom = model.getInstructions().stream().collect(Collectors.partitioningBy(instr -> instr.isCustomInstruction()));
        Map<Integer, List<InstructionModel>> builtinsGroupedByLength = instructionsGroupedByIsCustom.get(false).stream().collect(Collectors.groupingBy(instr -> instr.getInstructionLength()));

        for (Map.Entry<Integer, List<InstructionModel>> entry : builtinsGroupedByLength.entrySet()) {
            for (InstructionModel instr : entry.getValue()) {
                b.startCase().tree(createInstructionConstant(instr)).end();
            }
            b.startBlock();
            b.statement("bci += " + entry.getKey());
            b.statement("continue loop");
            b.end();
        }

        for (InstructionModel instr : instructionsGroupedByIsCustom.get(true)) {
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
        b.declaration(new ArrayCodeTypeMirror(types.Node), "result", "this.cachedNodes");

        b.startIf().string("result != null").end().startBlock();
        b.startReturn().string("result").end();
        b.end();

        b.startAssign("result").startCall("createCachedNodes").end(2);

        b.lineComment("For thread-safety, ensure that all stores into \"result\" happen before we make \"cachedNodes\" point to it.");
        b.startStatement().startStaticCall(context.getType(VarHandle.class), "storeStoreFence").end(2);
        b.startAssign("this.cachedNodes").string("result").end();
        b.startReturn().string("result").end();

        return ex;
    }

    private CodeExecutableElement createGetCachedNodes() {
        TypeMirror nodeArrayType = new ArrayCodeTypeMirror(types.Node);
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), nodeArrayType, "getCachedNodes");
        CodeTreeBuilder b = ex.createBuilder();

        b.declaration(new ArrayCodeTypeMirror(types.Node), "result", "this.cachedNodes");

        b.startIf().string("result == null").end().startBlock();
        b.tree(createTransferToInterpreterAndInvalidate("this"));
        b.startAssign("result").startCall("initializeCachedNodes").end(2);
        b.end();

        b.startReturn().string("result").end();
        b.end();

        return ex;
    }

    private CodeExecutableElement createDumpBytecode() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), context.getType(void.class), "dumpBytecode");
        CodeTreeBuilder b = ex.createBuilder();

        b.statement("int bci = 0");
        b.startWhile().string("bci < bc.length").end().startBlock();
        b.startSwitch().string(readBc("bci")).end().startBlock();
        for (InstructionModel instr : model.getInstructions()) {
            b.startCase().tree(createInstructionConstant(instr)).end().startBlock();

            // print code
            b.statement("String result = bci + \"\\t\" + \"" + instr.name + "\"");

            for (InstructionImmediate imm : instr.getImmediates()) {
                b.statement("result += \" " + imm.name + "=\"");
                switch (imm.kind) {
                    case BYTECODE_INDEX:
                    case INTEGER:
                    case LOCAL_SETTER:
                    case LOCAL_SETTER_RANGE_LENGTH:
                    case LOCAL_SETTER_RANGE_START:
                        b.statement("result += " + readBc("bci + " + imm.offset));
                        break;
                    case CONSTANT:
                        b.statement("result += " + readConst(readBc("bci + " + imm.offset)));
                        break;
                    case NODE:
                        b.statement("result += (cachedNodes == null) ? null : " + readNode(types.Node, readBc("bci + " + imm.offset)));
                        break;
                    default:
                        break;
                }
            }

            b.statement("System.out.println(result)");

            b.statement("bci += " + instr.getInstructionLength());
            b.statement("break");
            b.end();
        }

        b.caseDefault().startBlock();
        b.statement("break");
        b.end();

        b.end(); // } switch
        b.end(); // } while
        b.startAssert().string("bci == bc.length").end();

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
        final CodeVariableElement callback = addField(serializationState, Set.of(PRIVATE, FINAL), types.OperationSerializer, "callback");
        final CodeExecutableElement constructor = serializationState.add(createConstructorUsingFields(Set.of(), serializationState, null));
        final CodeVariableElement language = addField(serializationState, Set.of(PRIVATE), types.TruffleLanguage, "language");

        final CodeVariableElement labelCount = addField(serializationState, Set.of(PRIVATE), int.class, "labelCount");
        final CodeVariableElement objects = addField(serializationState, Set.of(PRIVATE, FINAL),
                        generic(HashMap.class, Object.class, Short.class), "objects");

        final CodeVariableElement[] codeBegin;
        final CodeVariableElement[] codeEnd;

        SerializationStateElements() {
            serializationState.getImplements().add(types.OperationSerializer_SerializerContext);

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
            serializationState.add(createWriteOperationNode());

        }

        private CodeExecutableElement createWriteOperationNode() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.OperationSerializer_SerializerContext, "writeOperationNode");
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
        CodeTypeElement nodePool = new CodeTypeElement(Set.of(PRIVATE, STATIC), ElementKind.CLASS, null, "NodePool");

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
                        new CodeVariableElement(Set.of(PRIVATE), generic(HashMap.class, types.OperationLabel, context.getType(int[].class)), "unresolvedLabels")));

        // This state is shared across all contexts for a given root node. It does not get
        // saved/restored when entering/leaving a FinallyTry.
        List<CodeVariableElement> builderContextInsensitiveState = new ArrayList<>(List.of(
                        new CodeVariableElement(Set.of(PRIVATE), new ArrayCodeTypeMirror(operationStackEntry.asType()), "operationStack"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "operationSp"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "numLocals"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "numLabels"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "numNodes"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int[].class), "stackValueBciStack"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "stackValueBciSp"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int[].class), "sourceIndexStack"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "sourceIndexSp"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int[].class), "sourceLocationStack"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "sourceLocationSp"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "opSeqNum"),
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

        class OperationStackEntryFactory {
            private CodeTypeElement create() {
                List<CodeVariableElement> fields = List.of(
                                new CodeVariableElement(Set.of(PRIVATE, FINAL), context.getType(int.class), "operation"),
                                new CodeVariableElement(Set.of(PRIVATE), context.getType(Object.class), "data"),
                                new CodeVariableElement(Set.of(PRIVATE, FINAL), context.getType(int.class), "sequenceNumber"),
                                new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "childCount"),
                                new CodeVariableElement(Set.of(PRIVATE), generic(context.getDeclaredType(ArrayList.class), types.OperationLabel), "declaredLabels"));

                operationStackEntry.addAll(fields);

                operationStackEntry.add(createConstructorUsingFields(Set.of(), operationStackEntry, null));
                operationStackEntry.add(createAddDeclaredLabel());

                return operationStackEntry;
            }

            private CodeExecutableElement createAddDeclaredLabel() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), context.getType(void.class), "addDeclaredLabel");
                ex.addParameter(new CodeVariableElement(types.OperationLabel, "label"));

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
                                new CodeVariableElement(generic(HashMap.class, context.getDeclaredType(Integer.class), types.OperationLabel), "handlerUnresolvedLabelsByIndex")));
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

        class DeserializerContextImplFactory {
            private CodeTypeElement create() {
                deserializerContextImpl.setEnclosingElement(operationNodeGen);
                deserializerContextImpl.getImplements().add(types.OperationDeserializer_DeserializerContext);

                deserializerContextImpl.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), generic(ArrayList.class, operationNodeGen.asType()), "builtNodes"));
                deserializerContextImpl.add(createConstructorUsingFields(Set.of(PRIVATE), deserializerContextImpl));

                deserializerContextImpl.add(createDeserializeOperationNode());

                return deserializerContextImpl;
            }

            private CodeExecutableElement createDeserializeOperationNode() {
                CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.OperationDeserializer_DeserializerContext, "readOperationNode");
                ex.renameArguments("buffer");
                CodeTreeBuilder b = ex.createBuilder();
                b.statement("return this.builtNodes.get(buffer.readChar())");
                return ex;
            }
        }

        private SerializationStateElements serializationElements;
        private CodeVariableElement serialization;

        private CodeTypeElement create() {
            builder.setSuperClass(types.OperationBuilder);
            builder.setEnclosingElement(operationNodeGen);

            builder.add(uninitialized);
            uninitialized.createInitBuilder().string(-1).end();

            builder.add(new SavedStateFactory().create());
            builder.add(new OperationStackEntryFactory().create());
            builder.add(new FinallyTryContextFactory().create());
            builder.add(new ConstantPoolFactory().create());

            builder.add(createOperationNames());

            builder.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), operationNodesImpl.asType(), "nodes"));
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
            builder.add(createCreateLabel());
            builder.add(createRegisterUnresolvedLabel());
            builder.add(createResolveUnresolvedLabels());
            builder.add(createReverseLabelMapping());

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
            builder.add(createInFinallyTryHandler());
            builder.add(createWriteShortInBounds());
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
            method.addParameter(new CodeVariableElement(types.OperationSerializer, "callback"));

            method.addThrownType(context.getType(IOException.class));
            CodeTreeBuilder b = method.createBuilder();
            b.statement("this.serialization = new SerializationState(builtNodes, buffer, callback)");

            b.startTryBlock();
            b.statement("nodes.getParser().parse(this)");

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

            method.addParameter(new CodeVariableElement(types.TruffleLanguage, "language"));
            method.addParameter(new CodeVariableElement(generic(Supplier.class, DataInput.class), "bufferSupplier"));
            method.addParameter(new CodeVariableElement(types.OperationDeserializer, "callback"));

            CodeTreeBuilder b = method.createBuilder();

            b.startTryBlock();

            b.statement("ArrayList<Object> consts = new ArrayList<>()");
            b.statement("ArrayList<OperationLocal> locals = new ArrayList<>()");
            b.statement("ArrayList<OperationLabel> labels = new ArrayList<>()");
            b.startStatement().type(type(DataInput.class)).string(" buffer = bufferSupplier.get()").end();

            b.startStatement();
            b.type(generic(context.getDeclaredType(ArrayList.class), operationNodeGen.asType()));
            b.string("builtNodes = new ArrayList<>()");
            b.end();

            b.startStatement();
            b.type(types.OperationDeserializer_DeserializerContext);
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
            for (OperationModel op : model.getOperations()) {

                // create begin/emit code
                b.startCase().staticReference(serializationElements.codeBegin[op.id]).end().startBlock();

                if (op.kind == OperationKind.INSTRUMENT_TAG && !hasTags) {
                    b.startThrow().startNew(context.getType(IllegalStateException.class));
                    b.doubleQuote(String.format("Cannot deserialize instrument tag. The language does not specify any tags with a @%s annotation.",
                                    ElementUtils.getSimpleName(types.ProvidedTags)));
                    b.end().end();
                    b.end(); // switch block
                    continue;
                }

                int i = 0;
                for (TypeMirror argType : op.operationArguments) {
                    String argumentName = "arg" + i;
                    if (ElementUtils.typeEquals(argType, types.TruffleLanguage)) {
                        b.declaration(types.TruffleLanguage, argumentName, "language");
                    } else if (ElementUtils.typeEquals(argType, types.OperationLocal)) {
                        b.statement("OperationLocal ", argumentName, " = locals.get(buffer.readShort())");
                    } else if (ElementUtils.typeEquals(argType, new ArrayCodeTypeMirror(types.OperationLocal))) {
                        b.statement("OperationLocal[] ", argumentName, " = new OperationLocal[buffer.readShort()]");
                        b.startFor().string("int i = 0; i < ", argumentName, ".length; i++").end().startBlock();
                        // this can be optimized since they are consecutive
                        b.statement(argumentName, "[i] = locals.get(buffer.readShort());");
                        b.end();
                    } else if (ElementUtils.typeEquals(argType, types.OperationLabel)) {
                        b.statement("OperationLabel ", argumentName, " = labels.get(buffer.readShort())");
                    } else if (ElementUtils.typeEquals(argType, context.getType(int.class))) {
                        b.statement("int ", argumentName, " = buffer.readInt()");
                    } else if (op.kind == OperationKind.INSTRUMENT_TAG && i == 0) {
                        b.startStatement().type(argType).string(" ", argumentName, " = TAG_INDEX_TO_CLASS[buffer.readShort()]").end();
                    } else if (ElementUtils.isObject(argType) || ElementUtils.typeEquals(argType, types.Source)) {
                        b.startStatement().type(argType).string(" ", argumentName, " = ").cast(argType).string("consts.get(buffer.readShort())").end();
                    } else {
                        throw new UnsupportedOperationException("cannot deserialize: " + argType);
                    }
                    i++;
                }

                b.startStatement();
                if (op.hasChildren()) {
                    b.startCall("begin" + op.name);
                } else {
                    b.startCall("emit" + op.name);
                }

                for (int j = 0; j < i; j++) {
                    b.string("arg" + j);
                }

                b.end(2); // statement, call

                b.statement("break");

                b.end(); // case block

                if (op.hasChildren()) {
                    b.startCase().staticReference(serializationElements.codeEnd[op.id]).end().startBlock();

                    if (op.kind == OperationKind.ROOT) {
                        b.startStatement();
                        b.type(model.getTemplateType().asType()).string(" node = ").string("end" + op.name + "()");
                        b.end();
                        b.startStatement().startCall("builtNodes.add").startGroup().cast(operationNodeGen.asType()).string("node").end().end().end();
                    } else {
                        b.statement("end", op.name, "()");
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
            b.startStatement().string("nodes.setSources(sources.toArray(new ").type(types.Source).string("[0]))").end();
            b.end();

            return ex;
        }

        private CodeExecutableElement createCreateLocal() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), types.OperationLocal, "createLocal");
            CodeTreeBuilder b = ex.createBuilder();

            if (model.enableSerialization) {
                b.startIf().string("serialization != null").end().startBlock();
                serializationWrapException(b, () -> {
                    serializationElements.writeShort(b, serializationElements.codeCreateLocal);
                });
                b.end();
            }

            b.startReturn().startNew(operationLocalImpl.asType()).string("numLocals++").end(2);

            return ex;
        }

        private CodeExecutableElement createCreateLabel() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), types.OperationLabel, "createLabel");
            CodeTreeBuilder b = ex.createBuilder();

            if (model.enableSerialization) {
                b.startIf().string("serialization != null").end().startBlock();
                serializationWrapException(b, () -> {
                    serializationElements.writeShort(b, serializationElements.codeCreateLabel);
                });

                b.startReturn().startNew(operationLabelImpl.asType());
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

            b.startAssign("OperationLabel result").startNew(operationLabelImpl.asType());
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
            ex.addParameter(new CodeVariableElement(types.OperationLabel, "label"));
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "bci"));

            CodeTreeBuilder b = ex.createBuilder();

            b.statement("int[] sites = unresolvedLabels.getOrDefault(label, new int[0])");
            b.statement("sites = Arrays.copyOf(sites, sites.length + 1)");
            b.statement("sites[sites.length-1] = bci");
            b.statement("unresolvedLabels.put(label, sites)");

            return ex;
        }

        private CodeExecutableElement createResolveUnresolvedLabels() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "resolveUnresolvedLabels");
            ex.addParameter(new CodeVariableElement(types.OperationLabel, "label"));

            CodeTreeBuilder b = ex.createBuilder();

            b.statement("OperationLabelImpl impl = (OperationLabelImpl) label");
            b.statement("assert impl.isDefined()");
            b.statement("int[] sites = unresolvedLabels.remove(impl)");
            b.startIf().string("sites != null").end().startBlock();
            b.startFor().string("int site : sites").end().startBlock();
            b.statement(writeBc("site", "(short) impl.index"));
            b.end(2);

            return ex;
        }

        private CodeExecutableElement createReverseLabelMapping() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), generic(HashMap.class, context.getDeclaredType(Integer.class), types.OperationLabel), "reverseLabelMapping");
            ex.addParameter(new CodeVariableElement(generic(HashMap.class, types.OperationLabel, context.getType(int[].class)), "unresolvedLabels"));

            CodeTreeBuilder b = ex.createBuilder();
            b.statement("HashMap<Integer, OperationLabel> result = new HashMap<>()");
            b.startFor().string("OperationLabel lbl : unresolvedLabels.keySet()").end().startBlock();
            b.startFor().string("int site : unresolvedLabels.get(lbl)").end().startBlock();
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

            int argIndex = 0;
            for (TypeMirror argument : operation.operationArguments) {
                ex.addParameter(new CodeVariableElement(argument, "arg" + argIndex));
                argIndex++;
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

                    b.statement("int index = sources.indexOf(arg0)");
                    b.startIf().string("index == -1").end().startBlock();
                    b.statement("index = sources.size()");
                    b.statement("sources.add(arg0)");
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

                    b.statement("sourceLocationStack[sourceLocationSp++] = arg0");
                    b.statement("sourceLocationStack[sourceLocationSp++] = arg1");

                    b.statement("doEmitSourceInfo(sourceIndexStack[sourceIndexSp - 1], arg0, arg1)");
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
                    b.statement("operationStack[operationSp - 1].data = finallyTryContext");

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
            b.statement("numLocals = 0");
            b.statement("numLabels = 0");

            if (model.hasBoxingElimination()) {
                b.statement("stackValueBciStack = new int[8]");
                b.statement("stackValueBciSp = 0");
            }

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
                int i = 0;
                for (TypeMirror argType : operation.operationArguments) {
                    if (ElementUtils.typeEquals(argType, types.TruffleLanguage)) {
                        b.statement("serialization.language = language");
                    } else if (ElementUtils.typeEquals(argType, types.OperationLocal)) {
                        serializationElements.writeShort(after, "(short) ((OperationLocalImpl) arg" + i + ").index");
                    } else if (ElementUtils.typeEquals(argType, new ArrayCodeTypeMirror(types.OperationLocal))) {
                        serializationElements.writeShort(after, "(short) arg" + i + ".length");
                        after.startFor().string("int i = 0; i < arg" + i + ".length; i++").end().startBlock();
                        serializationElements.writeShort(after, "(short) ((OperationLocalImpl) arg" + i + "[i]).index");
                        after.end();
                    } else if (ElementUtils.typeEquals(argType, types.OperationLabel)) {
                        serializationElements.writeShort(after, "(short) ((OperationLabelImpl) arg" + i + ").declaringOp");
                    } else if (ElementUtils.typeEquals(argType, context.getType(int.class))) {
                        serializationElements.writeInt(after, "arg" + i);
                    } else if (operation.kind == OperationKind.INSTRUMENT_TAG && i == 0) {
                        serializationElements.writeShort(after, "(short) CLASS_TO_TAG_INDEX.get(arg0)");
                    } else if (ElementUtils.isObject(argType) || ElementUtils.typeEquals(argType, types.Source)) {
                        String argumentName = "arg" + i;
                        String index = argumentName + "_index";
                        b.statement("short ", index, " = ", "serialization.serializeObject(", argumentName, ")");
                        serializationElements.writeShort(after, index);
                    } else {
                        throw new UnsupportedOperationException("cannot serialize: " + argType);
                    }
                    i++;
                }
                serializationElements.writeShort(b, serializationElements.codeBegin[operation.id]);

                b.tree(after.build());
            });
        }

        private void buildOperationBeginData(CodeTreeBuilder b, OperationModel operation) {
            switch (operation.kind) {
                case BLOCK:
                case INSTRUMENT_TAG:
                case SOURCE:
                case SOURCE_SECTION:
                    b.string("new Object[]{false}");
                    break;
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
                    b.string("arg0");
                    break;
                case CUSTOM_SIMPLE:
                    b.startNewArray(arrayOf(context.getType(Object.class)), null);
                    for (int i = 0; i < operation.operationArguments.length; i++) {
                        b.string("arg" + i);
                    }
                    b.end();
                    break;
                case CUSTOM_SHORT_CIRCUIT:
                    b.startNewArray(arrayOf(context.getType(Object.class)), null);
                    b.string("new int[0] /* branch fix-up indices */");
                    b.end();
                    break;
                case TRY_CATCH:
                    b.string("new int[]{" +
                                    "bci /* try start */, " +
                                    UNINIT + " /* try end */, " +
                                    UNINIT + " /* catch start */, " +
                                    UNINIT + " /* branch past catch fix-up index */, " +
                                    "curStack /* entry stack height */, " +
                                    "((OperationLocalImpl) arg0).index /* exception local index */}");
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
            b.string("\"Unexpected operation end, expected end\" + OPERATION_NAMES[entry.operation] + \", but got end \" + OPERATION_NAMES[id]").end();
            b.end(2);
            b.end(); // }

            b.startIf().string("entry.declaredLabels != null").end().startBlock();
            b.startFor().string("OperationLabel label : entry.declaredLabels").end().startBlock();
            b.statement("OperationLabelImpl impl = (OperationLabelImpl) label");
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
                    b.statement("FinallyTryContext ctx = (FinallyTryContext) operationStack[operationSp].data");
                    b.statement("short exceptionLocal = (short) numLocals++");
                    b.statement("int exHandlerIndex = doCreateExceptionHandler(ctx.bci, bci, " + UNINIT + " /* handler start */, curStack, exceptionLocal)");
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
                    buildEmitInstruction(b, model.throwInstruction, new String[]{"exceptionLocal"});
                    b.statement(writeBc("endBranchIndex", "(short) bci"));
                    break;
                case FINALLY_TRY_NO_EXCEPT:
                    b.statement("FinallyTryContext ctx = (FinallyTryContext) operationStack[operationSp].data");
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
            } else {
                b.string("" + !operation.isVoid);
            }
            b.end(2);

            return ex;
        }

        private CodeExecutableElement createEndRoot(OperationModel rootOperation) {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), context.getType(void.class), "endRoot");
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

            ex.setReturnType(model.templateType.asType());

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

            CodeTree newBuilderCall = CodeTreeBuilder.createBuilder().startStaticCall(types.FrameDescriptor, "newBuilder").string("numLocals + maxStack").end().build();
            b.declaration(types.FrameDescriptor_Builder, "fdb", newBuilderCall);
            b.startStatement().startCall("fdb.addSlots");
            b.string("numLocals + maxStack");
            b.staticReference(types.FrameSlotKind, "Illegal");
            b.end(2);

            b.startAssign("result").startNew(operationNodeGen.asType());
            b.string("language");
            b.string("fdb");
            b.end(2);

            b.startAssign("result.nodes").string("nodes").end();
            b.startAssign("result.bc").string("Arrays.copyOf(bc, bci)").end();
            b.startAssign("result.constants").string("constantPool.toArray()").end();
            if (model.enableTracing) {
                b.startAssign("result.basicBlockBoundary").string("Arrays.copyOf(basicBlockBoundary, bci)").end();
            }

            b.startAssign("result.numNodes").string("numNodes").end();

            if (model.enableYield) {
                b.startFor().string("ContinuationLocation location : continuationLocations").end().startBlock();
                b.statement("ContinuationLocationImpl locationImpl = (ContinuationLocationImpl) location");
                b.statement("locationImpl.rootNode = new ContinuationRoot(language, result.getFrameDescriptor(), result, (locationImpl.sp << 16) | locationImpl.bci)");
                b.end();
            }

            b.startAssign("result.handlers").string("Arrays.copyOf(exHandlers, exHandlerCount)").end();
            b.startAssign("result.numLocals").string("numLocals").end();
            b.startAssign("result.buildIndex").string("buildIndex").end();

            b.startAssert().string("builtNodes.size() == buildIndex").end();
            b.statement("builtNodes.add(result)");

            b.end(); // }

            b.statement("buildIndex++");

            b.startIf().string("withSource").end().startBlock();
            b.statement("result.sourceInfo = Arrays.copyOf(sourceInfo, sourceInfoIndex)");
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
            b.startBlock();
            String[] args = switch (operation.kind) {
                case LOAD_LOCAL -> new String[]{"((OperationLocalImpl) arg0).index"};
                case STORE_LOCAL, LOAD_LOCAL_MATERIALIZED, STORE_LOCAL_MATERIALIZED -> new String[]{"((OperationLocalImpl) operationStack[operationSp].data).index"};
                case RETURN -> new String[]{};
                case LOAD_ARGUMENT -> new String[]{"arg0"};
                case LOAD_CONSTANT -> new String[]{"constantPool.addConstant(arg0)"};
                case BRANCH -> {
                    b.startAssign("OperationLabelImpl label").string("(OperationLabelImpl) arg0").end();

                    b.statement("boolean isFound = false");
                    b.startFor().string("int i = 0; i < operationSp; i++").end().startBlock();
                    b.startIf().string("operationStack[i].sequenceNumber == label.declaringOp").end().startBlock();
                    b.statement("isFound = true");
                    b.statement("break");
                    b.end();
                    b.end();

                    b.startIf().string("!isFound").end().startBlock();
                    buildThrowIllegalStateException(b, "\"Branch must be targeting a label that is declared in an enclosing operation. Jumps into other operations are not permitted.\"");
                    b.end();

                    b.startIf().string("curStack != 0").end().startBlock();
                    buildThrowIllegalStateException(b, "\"Branch cannot be emitted in the middle of an operation.\"");
                    b.end();

                    b.statement("doEmitLeaves(label.declaringOp)");

                    b.startIf().string("label.isDefined()").end().startBlock();
                    buildThrowIllegalStateException(b, "\"Backward branches are unsupported. Use a While operation to model backward control flow.\"");
                    b.end();
                    // Mark the branch target as uninitialized. Add this location to a work list to
                    // be processed once the label is defined.
                    b.startStatement().startCall("registerUnresolvedLabel");
                    b.string("label");
                    b.string("bci + 1");
                    b.end(2);
                    b.newLine();
                    b.lineComment("We need to track branch targets inside finally handlers so that they can be adjusted each time the handler is emitted.");
                    b.startIf().string("label.finallyTryOp != " + UNINIT).end().startBlock();
                    // An earlier step has validated that the label is defined by an operation on
                    // the stack. We should be able to find the defining FinallyTry context without
                    // hitting an NPE.
                    b.statement("FinallyTryContext ctx = finallyTryContext");
                    b.startWhile().string("ctx.finallyTrySequenceNumber != label.finallyTryOp").end().startBlock();
                    b.statement("ctx = ctx.parentContext");
                    b.end();

                    b.startIf().string("inFinallyTryHandler(ctx)").end().startBlock();
                    b.statement("finallyTryContext.finallyRelativeBranches.add(bci + 1)");
                    b.end();

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
            b.end();
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

            if (operation.operationArguments != null) {
                int argIndex = 0;
                for (TypeMirror argument : operation.operationArguments) {
                    ex.addParameter(new CodeVariableElement(argument, "arg" + argIndex));
                    argIndex++;
                }
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
                b.startAssign("OperationLabelImpl label").string("(OperationLabelImpl) arg0").end();

                b.startIf().string("label.isDefined()").end().startBlock();
                buildThrowIllegalStateException(b, "\"OperationLabel already emitted. Each label must be emitted exactly once.\"");
                b.end();

                b.startIf().string("label.declaringOp != operationStack[operationSp - 1].sequenceNumber").end().startBlock();
                buildThrowIllegalStateException(b, "\"OperationLabel must be emitted inside the same operation it was created in.\"");
                b.end();

                b.startIf().string("curStack != 0").end().startBlock();
                buildThrowIllegalStateException(b, "\"OperationLabel cannot be emitted in the middle of an operation.\"");
                b.end();

                b.statement("label.index = bci");
                b.startStatement().startCall("resolveUnresolvedLabels");
                b.string("label");
                b.end(2);
            } else if (operation.instruction != null) {
                buildEmitOperationInstruction(b, operation);
            }

            b.startStatement().startCall("afterChild");
            b.string("" + !operation.isVoid);
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

            int localSetterIndex = 0;
            int localSetterRangeIndex = 0;
            for (int i = 0; i < immediates.size(); i++) {
                InstructionImmediate immediate = immediates.get(i);
                args[i] = switch (immediate.kind) {
                    case BYTECODE_INDEX -> UNINIT;
                    case LOCAL_SETTER -> {
                        String arg = "localSetter" + localSetterIndex;
                        b.startAssign("int " + arg);
                        if (inEmit) {
                            b.string("((OperationLocalImpl) arg" + (localSetterIndex + i) + ").index");
                        } else {
                            b.string("((OperationLocalImpl)((Object[]) operationStack[operationSp].data)[" + (localSetterIndex + i) + "]).index");
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
                        b.startAssign("OperationLocal[] " + locals);
                        if (inEmit) {
                            b.string("arg" + (localSetterIndex + localSetterRangeIndex + i));
                        } else {
                            b.string("(OperationLocal[]) ((Object[]) operationStack[operationSp].data)[" + (localSetterIndex + localSetterRangeIndex + i) + "]");
                        }
                        b.end();

                        // Convert to an array of local indices.
                        b.startAssign("int[] " + indices);
                        b.string("new int[" + locals + ".length]");
                        b.end();
                        b.startFor().string("int i = 0; i < " + indices + ".length; i++").end().startBlock();
                        b.startAssign(indices + "[i]").string("((OperationLocalImpl) " + locals + "[i]).index").end();
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
                    case INTEGER, CONSTANT -> throw new AssertionError("Operation " + operation.name + " takes an immediate " + immediate.name + " with unexpected kind " + immediate.kind +
                                    ". This is a bug in the Operation DSL processor.");
                };
            }

            return args;
        }

        private String[] buildCustomShortCircuitInitializer(CodeTreeBuilder b, OperationModel operation, InstructionModel instruction) {
            assert operation.kind == OperationKind.CUSTOM_SHORT_CIRCUIT;

            b.statement("int branchTarget = " + UNINIT);
            b.statement("int node = allocateNode()");
            b.lineComment("Add this location to a work list to be processed once the branch target is known.");
            b.statement("int[] sites = (int[]) ((Object[]) data)[0]");
            b.statement("sites = Arrays.copyOf(sites, sites.length + 1)");
            InstructionImmediate branchIndex = instruction.getImmediate(ImmediateKind.BYTECODE_INDEX);
            b.statement("sites[sites.length-1] = bci + " + branchIndex.offset);
            b.statement("((Object[]) data)[0] = sites");
            return new String[]{"branchTarget", "node"};
        }

        private CodeExecutableElement createBeforeChild() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "beforeChild");
            CodeTreeBuilder b = ex.createBuilder();

            createCheckRoot(b);

            b.statement("Object data = operationStack[operationSp - 1].data");
            b.statement("int childIndex = operationStack[operationSp - 1].childCount");

            b.startSwitch().string("operationStack[operationSp - 1].operation").end().startBlock();

            for (OperationModel op : model.getOperations()) {
                if (!op.hasChildren()) {
                    continue;
                }

                b.startCase().tree(createOperationConstant(op)).end().startBlock();

                if (op.isTransparent && (op.isVariadic || op.numChildren > 1)) {
                    b.startIf().string("(boolean) ((Object[]) data)[0]").end().startBlock();
                    buildEmitInstruction(b, model.popInstruction, null);
                    b.end();
                }

                if (op.kind == OperationKind.CUSTOM_SHORT_CIRCUIT) {
                    b.startIf().string("childIndex != 0").end().startBlock();
                    String[] args = buildCustomShortCircuitInitializer(b, op, op.instruction);
                    buildEmitInstruction(b, op.instruction, args);
                    b.end();
                }

                b.statement("break");
                b.end();
            }

            b.end();

            return ex;
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
            CodeTreeBuilder b = ex.createBuilder();

            b.statement("Object data = operationStack[operationSp - 1].data");
            b.statement("int childIndex = operationStack[operationSp - 1].childCount");

            b.startSwitch().string("operationStack[operationSp - 1].operation").end().startBlock();

            for (OperationModel op : model.getOperations()) {
                if (!op.hasChildren()) {
                    continue;
                }

                b.startCase().tree(createOperationConstant(op)).end().startBlock();

                if (op.childrenMustBeValues != null && !op.isTransparent) {
                    // this can be optimized a bit, by merging all the throw cases into one, and all
                    // the pop cases into the other
                    for (int i = 0; i < op.childrenMustBeValues.length; i++) {
                        b.startIf().string("childIndex ", (i == op.childrenMustBeValues.length - 1 && op.isVariadic) ? ">=" : "==", " " + i).end().startBlock();
                        if (op.childrenMustBeValues[i]) {
                            b.startIf().string("!producedValue").end().startBlock();
                            b.startThrow().startNew(context.getType(IllegalStateException.class));
                            b.string("\"Operation " + op.name + " expected a value-producing child at position \"",
                                            " + childIndex + ",
                                            "\", but a void one was provided. This likely indicates a bug in the parser.\"");
                            b.end(2);
                            b.end();
                        } else {
                            b.startIf().string("producedValue").end().startBlock();
                            buildEmitInstruction(b, model.popInstruction, null);
                            b.end();
                        }
                        b.end();
                    }
                }

                if (op.isTransparent) {
                    b.statement("((Object[]) data)[0] = producedValue");
                }

                switch (op.kind) {
                    case IF_THEN:
                        b.startIf().string("childIndex == 0").end().startBlock();
                        b.statement("((int[]) data)[0] = bci + 1");
                        emitFinallyRelativeBranchCheck(b, 1);
                        buildEmitInstruction(b, model.branchFalseInstruction, new String[]{UNINIT});
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
                        buildEmitInstruction(b, model.branchFalseInstruction, new String[]{UNINIT});
                        b.end().startElseIf().string("childIndex == 1").end().startBlock();
                        b.statement("((int[]) data)[1] = bci + 1");
                        emitFinallyRelativeBranchCheck(b, 1);
                        buildEmitInstruction(b, model.branchInstruction, new String[]{UNINIT});
                        if (op.kind == OperationKind.CONDITIONAL) {
                            // we have to adjust the stack for the third child
                            b.statement("curStack -= 1");
                            if (model.hasBoxingElimination()) {
                                b.statement("stackValueBciSp -= 1");
                            }
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
                        buildEmitInstruction(b, model.branchFalseInstruction, new String[]{UNINIT});
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

                        b.declaration(
                                        generic(HashMap.class, context.getDeclaredType(Integer.class), types.OperationLabel),
                                        "unresolvedLabelsByIndex",
                                        CodeTreeBuilder.createBuilder().startStaticCall(operationBuilderType, "reverseLabelMapping").string("unresolvedLabels").end());

                        b.startStatement().startCall("finallyTryContext", "setHandler");
                        b.string("Arrays.copyOf(bc, bci)");
                        b.string("maxStack");
                        b.string("withSource ? Arrays.copyOf(sourceInfo, sourceInfoIndex) : null");
                        b.string("Arrays.copyOf(exHandlers, exHandlerCount)");
                        b.string("unresolvedLabelsByIndex");
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
                            b.statement("OperationLabelImpl lbl = (OperationLabelImpl) context.handlerUnresolvedLabelsByIndex.get(branchIdx)");
                            b.statement("assert !lbl.isDefined()");
                            b.startStatement().startCall("registerUnresolvedLabel");
                            b.string("lbl");
                            b.string("offsetBci + branchIdx");
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
                        b.statement(writeBc("offsetBci + branchIdx", "(short) branchTarget"));
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
                            b.statement("int newOffset = " + readBc("offsetBci + handlerBci + " + immediate.offset) + " + offsetBci /* adjust " + immediate.name + " */");
                            b.statement(writeBc("offsetBci + handlerBci + " + immediate.offset, "(short) newOffset"));
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
            b.statement("exHandlers[exHandlerCount + idx + 3] = context.handlerExHandlers[idx + 3]");
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

        private void buildPushStackIndex(CodeTreeBuilder b, String index, boolean performCheck) {
            if (performCheck) {
                b.startIf().string("stackValueBciStack.length == stackValueBciSp").end().startBlock();
                b.statement("stackValueBciStack = Arrays.copyOf(stackValueBciStack, stackValueBciStack.length * 2)");
                b.end();
            }

            if (index != null) {
                if (index.equals("0")) {
                    b.statement("stackValueBciStack[stackValueBciSp++] = bci");
                } else {
                    b.statement("stackValueBciStack[stackValueBciSp++] = ((" + index + ") << 16 | bci");
                }
            } else {
                b.statement("stackValueBciStack[stackValueBciSp++] = 0xffff0000");
            }

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
                case CUSTOM_SHORT_CIRCUIT:
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

            b.statement("FinallyTryContext ctx = (FinallyTryContext) operationStack[i].data");
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

        private CodeExecutableElement createInFinallyTryHandler() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(boolean.class), "inFinallyTryHandler");
            ex.addParameter(new CodeVariableElement(finallyTryContext.asType(), "context"));
            CodeTreeBuilder b = ex.createBuilder();

            b.startReturn();
            b.string("context != null && (!context.handlerIsSet() || inFinallyTryHandler(context.parentContext))");
            b.end();

            return ex;
        }

        private CodeExecutableElement createWriteShortInBounds() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "writeShortInBounds");
            ex.addParameter(new CodeVariableElement(context.getType(short[].class), "array"));
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "index"));
            ex.addParameter(new CodeVariableElement(context.getType(short.class), "value"));
            CodeTreeBuilder b = ex.createBuilder();

            b.statement("array[index] = value");

            return ex;
        }

        private static String writeBc(String index, String value) {
            return String.format("writeShortInBounds(bc, %s, %s)", index, value);
        }

        private static String readHandlerBc(String index) {
            return String.format("readShortInBounds(handlerBc, %s)", index);
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
            ctor.addParameter(new CodeVariableElement(operationNodesImpl.asType(), "nodes"));
            ctor.addParameter(new CodeVariableElement(context.getType(boolean.class), "isReparse"));
            ctor.addParameter(new CodeVariableElement(types.OperationConfig, "config"));

            CodeTreeBuilder b = ctor.createBuilder();

            b.statement("this.nodes = nodes");
            b.statement("this.isReparse = isReparse");
            b.statement("this.withSource = config.isWithSource()");
            b.statement("this.withInstrumentation = config.isWithInstrumentation()");
            b.statement("this.sources = this.withSource ? new ArrayList<>() : null");
            b.statement("this.builtNodes = new ArrayList<>()");
            b.statement("this.constantPool = new ConstantPool()");

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

    class OperationNodesImplFactory {
        private CodeTypeElement create() {
            operationNodesImpl.setSuperClass(generic(types.OperationNodes, model.templateType.asType()));
            operationNodesImpl.setEnclosingElement(operationNodeGen);

            operationNodesImpl.add(createConstructor());
            operationNodesImpl.add(createReparseImpl());
            operationNodesImpl.add(createSetNodes());
            operationNodesImpl.add(createSetSources());
            operationNodesImpl.add(createGetSources());

            operationNodesImpl.add(createGetParser());

            return operationNodesImpl;
        }

        private CodeExecutableElement createConstructor() {
            CodeExecutableElement ctor = new CodeExecutableElement(null, "OperationNodesImpl");
            ctor.addParameter(new CodeVariableElement(parserType, "generator"));

            ctor.createBuilder().statement("super(generator)");
            return ctor;
        }

        private CodeExecutableElement createReparseImpl() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.OperationNodes, "reparseImpl");
            ex.renameArguments("config", "parse", "nodes");
            CodeTreeBuilder b = ex.createBuilder();

            b.declaration(builder.asType(), "builder",
                            b.create().startNew(builder.asType()).string("this").string("true").string("config").end().build());
            b.startStatement().startCall("builder.builtNodes.addAll");
            b.startGroup().string("(List) ");
            b.startStaticCall(context.getType(List.class), "of").string("nodes").end();
            b.end();
            b.end(2);

            // TODO: shouldn't we be somehow re-processing the input? Right now this is a no-op.

            return ex;
        }

        private CodeExecutableElement createGetParser() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE),
                            parserType, "getParser");
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn();
            b.cast(parserType).string("parse");
            b.end();
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
            return interpreterType;
        }

        private List<CodeExecutableElement> createContinueAt() {
            // This method returns a list containing the continueAt method plus helper methods for
            // custom instructions. The helper methods help reduce the bytecode size of the dispatch
            // loop.
            List<CodeExecutableElement> results = new ArrayList<>();

            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC, FINAL), context.getType(int.class), "continueAt");
            ex.addParameter(new CodeVariableElement(operationNodeGen.asType(), "$this"));
            ex.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));
            if (model.enableYield) {
                ex.addParameter(new CodeVariableElement(types.VirtualFrame, "generatorFrame"));
            }
            ex.addParameter(new CodeVariableElement(context.getType(short[].class), "bc"));
            ex.addParameter(new CodeVariableElement(context.getType(Object[].class), "constants"));
            if (!tier.isUncached) {
                ex.addParameter(new CodeVariableElement(new ArrayCodeTypeMirror(types.Node), "cachedNodes"));
            }
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "startState"));

            results.add(ex);

            CodeTreeBuilder b = ex.createBuilder();

            if (!tier.isUncached) {
                ex.addAnnotationMirror(createExplodeLoopAnnotation("MERGE_EXPLODE"));
            }

            b.statement("int bci = startState & 0xffff");
            b.statement("int sp = (startState >> 16) & 0xffff");

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

                b.startDoc();
                b.lines(instr.infodump());
                b.end();

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
                            b.statement("$this.changeInterpreters(TIER1)");
                            b.statement("return (sp << 16) | " + readBc("bci + 1"));
                            b.end();
                        } else {
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
                            b.statement("frame.setObject(sp, osrResult)");
                            b.statement("sp++");
                            b.startReturn().string("((sp - 1) << 16) | 0xffff").end();
                            b.end();

                            b.end();
                        }
                        b.statement("bci = + " + readBc("bci + 1"));
                        b.statement("continue loop");
                        break;
                    case BRANCH_FALSE:
                        b.startIf().string("(Boolean) frame.getObject(sp - 1) == Boolean.TRUE").end().startBlock();
                        b.statement("sp -= 1");
                        b.statement("bci += 2");
                        b.statement("continue loop");
                        b.end().startElseBlock();
                        b.statement("sp -= 1");
                        b.statement("bci = " + readBc("bci + 1"));
                        b.statement("continue loop");
                        b.end();
                        break;
                    case INSTRUMENTATION_ENTER:
                        break;
                    case INSTRUMENTATION_EXIT:
                        break;
                    case INSTRUMENTATION_LEAVE:
                        break;
                    case LOAD_ARGUMENT:
                        b.statement("frame.setObject(sp, frame.getArguments()[" + readBc("bci + 1") + "])");
                        b.statement("sp += 1");
                        break;
                    case LOAD_CONSTANT:
                        b.statement("frame.setObject(sp, " + readConst(readBc("bci + 1")) + ")");
                        b.statement("sp += 1");
                        break;
                    case LOAD_LOCAL: {
                        String localFrame = model.enableYield ? "generatorFrame" : "frame";
                        b.statement("frame.setObject(sp, " + localFrame + ".getObject(" + readBc("bci + 1") + "))");
                        b.statement("sp += 1");
                        break;
                    }
                    case LOAD_LOCAL_MATERIALIZED:
                        b.statement("frame.setObject(sp - 1, ((VirtualFrame) frame.getObject(sp - 1)).getObject(" + readBc("bci + 1") + "))");
                        break;
                    case POP:
                        b.statement("frame.clear(sp - 1)");
                        b.statement("sp -= 1");
                        break;
                    case RETURN:
                        if (tier.isUncached) {
                            b.startIf().string("uncachedExecuteCount-- <= 0").end().startBlock();
                            b.tree(createTransferToInterpreterAndInvalidate("$this"));
                            b.statement("$this.changeInterpreters(TIER1)");
                            b.end().startElseBlock();
                            b.statement("$this.uncachedExecuteCount = uncachedExecuteCount");
                            b.end();
                        }

                        b.statement("return ((sp - 1) << 16) | 0xffff");
                        break;
                    case STORE_LOCAL: {
                        String localFrame = model.enableYield ? "generatorFrame" : "frame";
                        b.statement(localFrame + ".setObject(" + readBc("bci + 1") + ", frame.getObject(sp - 1))");
                        b.statement("frame.clear(sp - 1)");
                        b.statement("sp -= 1");
                        break;
                    }
                    case STORE_LOCAL_MATERIALIZED:
                        b.statement("((VirtualFrame) frame.getObject(sp - 2)).setObject(" + readBc("bci + 1") + ", frame.getObject(sp - 1))");
                        b.statement("frame.clear(sp - 1)");
                        b.statement("frame.clear(sp - 2)");
                        b.statement("sp -= 2");
                        break;
                    case THROW:
                        b.statement("throw sneakyThrow((Throwable) frame.getObject(" + readBc("bci + 1") + "))");
                        break;
                    case YIELD:
                        b.statement("int numLocals = $this.numLocals");
                        b.statement("frame.copyTo(numLocals, generatorFrame, numLocals, (sp - 1 - numLocals))");
                        b.statement("frame.setObject(sp - 1, ((ContinuationLocation) " + readConst(readBc("bci + 1")) + ").createResult(generatorFrame, frame.getObject(sp - 1)))");
                        b.statement("return (((sp - 1) << 16) | 0xffff)");
                        break;
                    case SUPERINSTRUCTION:
                        // todo: implement superinstructions
                        break;
                    case STORE_NULL:
                        b.statement("frame.setObject(sp, null)");
                        b.statement("sp += 1");
                        break;
                    case LOAD_VARIADIC:
                        int effect = -instr.variadicPopCount + 1;
                        b.startStatement();
                        b.string("frame.setObject(sp");
                        if (instr.variadicPopCount != 0) {
                            b.string(" - ").string(instr.variadicPopCount);
                        }
                        b.string(", ");
                        if (instr.variadicPopCount == 0) {
                            b.staticReference(emptyObjectArray);
                        } else {
                            b.string("readVariadic(frame, sp, ").string(instr.variadicPopCount).string(")");
                        }
                        b.string(")");
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
                        b.statement("frame.setObject(sp - 1, mergeVariadic((Object[]) frame.getObject(sp - 1)))");
                        break;
                    case CUSTOM: {
                        results.add(buildCustomInstructionExecute(b, instr, false));
                        break;
                    }
                    case CUSTOM_SHORT_CIRCUIT:
                        results.add(buildCustomInstructionExecute(b, instr, true));

                        b.startIf().string("result", instr.continueWhen ? " != " : " == ", "Boolean.TRUE").end().startBlock();
                        // don't pop (the argument used in the SC op is the result)
                        b.statement("bci = " + readBc("bci + 1"));
                        b.statement("continue loop");
                        b.end().startElseBlock();
                        b.statement("frame.clear(sp - 1)");
                        b.statement("sp -= 1");
                        b.statement("bci += " + instr.getInstructionLength());
                        b.statement("continue loop");
                        b.end();
                        break;

                    default:
                        throw new UnsupportedOperationException("not implemented");
                }

                if (!instr.isControlFlow()) {
                    b.statement("bci += " + instr.getInstructionLength());
                    b.statement("continue loop");
                }

                b.end();

            }

            b.end(); // switch

            b.end().startCatchBlock(context.getDeclaredType("com.oracle.truffle.api.exception.AbstractTruffleException"), "ex");

            b.statement("int[] handlers = $this.handlers");
            b.startFor().string("int idx = 0; idx < handlers.length; idx += 5").end().startBlock();

            // todo: this could get improved
            b.startIf().string("handlers[idx] > bci").end().startBlock().statement("continue").end();
            b.startIf().string("handlers[idx + 1] <= bci").end().startBlock().statement("continue").end();

            b.statement("bci = handlers[idx + 2]");
            b.statement("sp = handlers[idx + 3] + $this.numLocals");
            b.statement("frame.setObject(handlers[idx + 4], ex)");

            b.statement("continue loop");

            b.end(); // for

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

        // Generate a helper method that implements the custom instruction. Also emits a call to the
        // helper inside continueAt.
        private CodeExecutableElement buildCustomInstructionExecute(CodeTreeBuilder continueAtBuilder, InstructionModel instr, boolean isShortCircuit) {
            // To reduce bytecode in the dispatch loop, extract each implementation into a helper.
            String helperName = customInstructionHelperName(instr);

            TypeMirror returnType = isShortCircuit ? context.getType(Object.class) : context.getType(void.class);
            CodeExecutableElement helper = new CodeExecutableElement(Set.of(PRIVATE, STATIC, FINAL), returnType, helperName);

            helper.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));

            if (!tier.isUncached) {
                helper.addParameter(new CodeVariableElement(new ArrayCodeTypeMirror(types.Node), "cachedNodes"));
                if (model.enableTracing) {
                    helper.addParameter(new CodeVariableElement(types.ExecutionTracer, "tracer"));
                }
            }

            List<CodeVariableElement> extraParams = List.of(
                            new CodeVariableElement(operationNodeGen.asType(), "$this"),
                            new CodeVariableElement(context.getType(short[].class), "bc"),
                            new CodeVariableElement(context.getType(int.class), "bci"),
                            new CodeVariableElement(context.getType(int.class), "sp"));

            helper.getParameters().addAll(extraParams);

            TypeMirror cachedType = new GeneratedTypeMirror("", cachedDataClassName(instr));
            boolean isVoid = instr.signature.isVoid;

            CodeTreeBuilder b = helper.createBuilder();

            // Since an instruction produces at most one value, stackEffect is at most 1.
            int stackEffect = (instr.signature.isVoid ? 0 : 1) - instr.signature.valueCount;

            if (!tier.isUncached) {
                // If not tier 0, retrieve the node.
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
            } else if (isShortCircuit) {
                assert stackEffect == 0 : "Short circuit operation should push and pop a single value.";
                b.startReturn();
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

            b.string("frame");

            // The tier 0 version takes all of its parameters. Other versions compute them.
            if (tier.isUncached) {
                for (int i = 0; i < instr.signature.valueCount; i++) {
                    TypeMirror targetType = instr.signature.getParameterType(i);
                    b.startGroup();
                    if (!ElementUtils.isObject(targetType)) {
                        b.cast(targetType);
                    }
                    b.startCall("frame.getObject").startGroup();
                    b.string("sp");
                    b.string(" - " + (instr.signature.valueCount - i));
                    b.end(2);
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

            b.variables(extraParams);
            b.end(2);

            // Update the stack.
            if (!isVoid && !isShortCircuit) {
                if (stackEffect == 1) {
                    b.statement("frame.setObject(sp, result)");
                } else {
                    b.statement("frame.setObject(sp - " + (1 - stackEffect) + ", result)");
                }
            }
            for (int i = stackEffect; i < 0; i++) {
                // When stackEffect is negative, values should be cleared from the top of the stack.
                b.statement("frame.clear(sp - " + -i + ")");
            }

            // In continueAt, call the helper and adjust sp.
            if (isShortCircuit) {
                // continueAt needs the boolean result to decide whether to branch.
                continueAtBuilder.startAssign("Object result");
            } else {
                continueAtBuilder.startStatement();
            }
            continueAtBuilder.startCall(helperName);
            continueAtBuilder.variables(helper.getParameters());
            continueAtBuilder.end(2);

            if (stackEffect > 0) {
                continueAtBuilder.statement("sp += " + stackEffect);
            } else if (stackEffect < 0) {
                continueAtBuilder.statement("sp -= " + -stackEffect);
            }

            return helper;
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

    private CodeExecutableElement createReadShortInBounds() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), context.getType(short.class), "readShortInBounds");
        ex.addParameter(new CodeVariableElement(context.getType(short[].class), "array"));
        ex.addParameter(new CodeVariableElement(context.getType(int.class), "index"));
        CodeTreeBuilder b = ex.getBuilder();

        b.startReturn();
        if (model.allowUnsafe) {
            b.startCall("UNSAFE", "getShort").string("array").string("Unsafe.ARRAY_SHORT_BASE_OFFSET + index * Unsafe.ARRAY_SHORT_INDEX_SCALE").end();
        } else {
            b.string("array[index]");
        }
        b.end();

        return ex;
    }

    private CodeExecutableElement createReadNodeInBounds() {
        CodeTypeParameterElement T = new CodeTypeParameterElement(CodeNames.of("T"), types.Node);
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), T.asType(), "readNodeInBounds");
        ex.getTypeParameters().add(T);

        ex.addParameter(new CodeVariableElement(new ArrayCodeTypeMirror(types.Node), "array"));
        ex.addParameter(new CodeVariableElement(context.getType(int.class), "index"));
        ex.addParameter(new CodeVariableElement(generic(context.getType(Class.class), T.asType()), "expectedType"));
        CodeTreeBuilder b = ex.getBuilder();

        b.startReturn().cast(T.asType());
        if (model.allowUnsafe) {
            b.startCall("UNSAFE", "getObject").string("array").string("Unsafe.ARRAY_OBJECT_BASE_OFFSET + index * Unsafe.ARRAY_OBJECT_INDEX_SCALE").end();
        } else {
            b.string("array[index]");
        }
        b.end();

        addSuppressWarnings(context, ex, "unchecked");

        return ex;
    }

    private CodeExecutableElement createReadObjectInBounds() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), context.getType(Object.class), "readObjectInBounds");
        ex.addParameter(new CodeVariableElement(context.getType(Object[].class), "array"));
        ex.addParameter(new CodeVariableElement(context.getType(int.class), "index"));
        CodeTreeBuilder b = ex.getBuilder();

        b.startReturn();
        if (model.allowUnsafe) {
            b.startCall("UNSAFE", "getObject").string("array").string("Unsafe.ARRAY_OBJECT_BASE_OFFSET + index * Unsafe.ARRAY_OBJECT_INDEX_SCALE").end();
        } else {
            b.string("array[index]");
        }
        b.end();

        return ex;
    }

    class OSRMembersFactory {
        final String METADATA_FIELD_NAME = "osrMetadata_";

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

    class OperationLocalImplFactory {
        private CodeTypeElement create() {
            operationLocalImpl.setSuperClass(generic(types.OperationLocal, model.templateType.asType()));
            operationLocalImpl.setEnclosingElement(operationNodeGen);

            operationLocalImpl.add(new CodeVariableElement(context.getType(int.class), "index"));

            operationLocalImpl.add(createConstructorUsingFields(Set.of(), operationLocalImpl, null));

            return operationLocalImpl;
        }
    }

    class OperationLabelImplFactory {
        private CodeTypeElement create() {
            operationLabelImpl.setSuperClass(generic(types.OperationLabel, model.templateType.asType()));
            operationLabelImpl.setEnclosingElement(operationNodeGen);

            operationLabelImpl.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), context.getType(int.class), "id"));
            operationLabelImpl.add(new CodeVariableElement(context.getType(int.class), "index"));
            operationLabelImpl.add(new CodeVariableElement(context.getType(int.class), "declaringOp"));
            operationLabelImpl.add(new CodeVariableElement(context.getType(int.class), "finallyTryOp"));

            operationLabelImpl.add(createConstructorUsingFields(Set.of(), operationLabelImpl, null));
            operationLabelImpl.add(createIsDefined());
            operationLabelImpl.add(createEquals());
            operationLabelImpl.add(createHashCode());

            return operationLabelImpl;
        }

        private CodeExecutableElement createIsDefined() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), context.getType(boolean.class), "isDefined");
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn().string("index != ").staticReference(operationBuilderType, BuilderFactory.UNINIT).end();
            return ex;
        }

        private CodeExecutableElement createEquals() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), context.getType(boolean.class), "equals");
            ex.addParameter(new CodeVariableElement(context.getType(Object.class), "other"));

            CodeTreeBuilder b = ex.createBuilder();
            b.startIf().string("!(other instanceof OperationLabelImpl)").end().startBlock();
            b.returnFalse();
            b.end();

            b.startReturn().string("this.id == ((OperationLabelImpl) other).id").end();
            return ex;
        }

        private CodeExecutableElement createHashCode() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), context.getType(int.class), "hashCode");
            CodeTreeBuilder b = ex.createBuilder();

            b.startReturn().string("this.id").end();
            return ex;
        }
    }

    class ContinuationRootFactory {
        private CodeTypeElement create() {
            continuationRoot.setEnclosingElement(operationNodeGen);
            continuationRoot.setSuperClass(types.RootNode);

            continuationRoot.add(new CodeVariableElement(Set.of(FINAL), operationNodeGen.asType(), "root"));
            continuationRoot.add(new CodeVariableElement(Set.of(FINAL), context.getType(int.class), "target"));
            continuationRoot.add(GeneratorUtils.createConstructorUsingFields(
                            Set.of(), continuationRoot,
                            ElementFilter.constructorsIn(((TypeElement) types.RootNode.asElement()).getEnclosedElements()).stream().filter(x -> x.getParameters().size() == 2).findFirst().get()));

            continuationRoot.add(createExecute());

            continuationRoot.add(createToString());

            return continuationRoot;
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
            b.statement("parentFrame.copyTo(root.numLocals, frame, root.numLocals, sp - 1 - root.numLocals)");
            b.statement("frame.setObject(sp - 1, inputValue)");

            b.statement("return root.continueAt(frame, parentFrame, (sp << 16) | (target & 0xffff))");

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
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), context.getType(void.class), "validateArgument");
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
        private void process(CodeTypeElement el, InstructionModel instr) {
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

            if (instr.signature.resultBoxingElimination) {
                el.getInterfaces().add(boxableInterface.asType());
                el.add(createSetBoxing(instr));
            }

            if (OperationsNodeFactory.this.model.enableBaselineInterpreter) {
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

        private CodeExecutableElement createSetBoxing(@SuppressWarnings("unused") InstructionModel instr) {
            CodeExecutableElement setBoxing = GeneratorUtils.overrideImplement((DeclaredType) boxableInterface.asType(), "setBoxing");
            CodeTreeBuilder b = setBoxing.createBuilder();

            b.tree(createNeverPartOfCompilation());

            b.startAssert().string("index == 0").end();
            b.statement("this.op_resultType_ = kind");
            return setBoxing;
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

    private static CodeTree boxingTypeToInt(TypeMirror mir) {
        if (!ElementUtils.isPrimitive(mir)) {
            throw new AssertionError();
        }

        return CodeTreeBuilder.singleString(mir.getKind().ordinal() + 1 + " /* " + mir + " */ ");
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

    private static String readBc(String index) {
        return String.format("readShortInBounds(bc, %s)", index);
    }

    private static String readConst(String index) {
        return String.format("readObjectInBounds(constants, %s)", index);
    }

    private static String readNode(TypeMirror expectedType, String index) {
        return String.format("readNodeInBounds(cachedNodes, %s, %s.class)", index, expectedType);
    }

    private static String cachedDataClassName(InstructionModel instr) {
        return instr.getInternalName() + "Gen";
    }

    private static String childString(int numChildren) {
        return numChildren + ((numChildren == 1) ? " child" : " children");
    }
}
