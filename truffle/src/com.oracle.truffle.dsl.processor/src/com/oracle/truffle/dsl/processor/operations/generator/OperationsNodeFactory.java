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
import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.createShouldNotReachHere;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

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
import com.oracle.truffle.dsl.processor.operations.model.InstructionModel.InstructionField;
import com.oracle.truffle.dsl.processor.operations.model.InstructionModel.InstructionKind;
import com.oracle.truffle.dsl.processor.operations.model.OperationModel;
import com.oracle.truffle.dsl.processor.operations.model.OperationModel.CustomSignature;
import com.oracle.truffle.dsl.processor.operations.model.OperationModel.OperationKind;
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

    // The interpreter classes that execute the bytecode.
    private final CodeTypeElement baseInterpreter = new CodeTypeElement(Set.of(PRIVATE, STATIC, ABSTRACT), ElementKind.CLASS, null, "BaseInterpreter");
    private final CodeTypeElement uncachedInterpreter;
    private final CodeTypeElement cachedInterpreter = new CodeTypeElement(Set.of(PRIVATE, STATIC), ElementKind.CLASS, null, "CachedInterpreter");
    private final CodeTypeElement instrumentableInterpreter = new CodeTypeElement(Set.of(PRIVATE, STATIC), ElementKind.CLASS, null, "InstrumentableInterpreter");

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
    // Class that allows us to store and overwrite integer constants without performing additional
    // boxing.
    private final CodeTypeElement intRef = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "IntRef");
    private final CodeTypeElement loadLocalData = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "LoadLocalData");
    private final CodeTypeElement storeLocalData = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "StoreLocalData");

    // Root node and ContinuationLocal classes to support yield.
    private final CodeTypeElement continuationRoot;
    private final CodeTypeElement continuationLocationImpl;

    // Singleton field for an empty array.
    private final CodeVariableElement emptyObjectArray;

    public OperationsNodeFactory(OperationsModel model) {
        this.model = model;
        operationNodeGen = GeneratorUtils.createClass(model.templateType, null, Set.of(PUBLIC, FINAL), model.templateType.getSimpleName() + "Gen", model.templateType.asType());
        emptyObjectArray = addField(operationNodeGen, Set.of(PRIVATE, STATIC, FINAL), Object[].class, "EMPTY_ARRAY", "new Object[0]");

        if (model.generateUncached) {
            uncachedInterpreter = model.generateUncached ? new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "UncachedInterpreter") : null;
        } else {
            uncachedInterpreter = null;
        }

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

        // Define the interpreter implementations. The root node defines fields for the current
        // interpreter and for each variant.
        operationNodeGen.add(new BaseInterpreterFactory().create());
        if (model.generateUncached) {
            operationNodeGen.add(new InterpreterFactory(uncachedInterpreter, true, false).create());
            operationNodeGen.add(createInterpreterVariantField(uncachedInterpreter, "UNCACHED"));
        }
        operationNodeGen.add(new InterpreterFactory(cachedInterpreter, false, false).create());
        operationNodeGen.add(createInterpreterVariantField(cachedInterpreter, "CACHED"));
        operationNodeGen.add(new InterpreterFactory(instrumentableInterpreter, false, true).create());
        operationNodeGen.add(createInterpreterVariantField(instrumentableInterpreter, "INSTRUMENTABLE"));
        operationNodeGen.add(createInterpreterField());

        // Define the builder class.
        operationNodeGen.add(new BuilderFactory().create());

        // Define implementations for the public classes that Truffle interpreters interact with.
        operationNodeGen.add(new OperationNodesImplFactory().create());
        operationNodeGen.add(new OperationLocalImplFactory().create());
        operationNodeGen.add(new OperationLabelImplFactory().create());

        // Define helper classes containing the constants for instructions and operations.
        operationNodeGen.add(new InstructionConstantsFactory().create());
        operationNodeGen.add(new OperationsConstantsFactory().create());

        // Define the classes that model instruction data (e.g., branches, inline caches).
        operationNodeGen.add(new BoxableInterfaceFactory().create());
        operationNodeGen.add(new IntRefFactory().create());
        if (model.hasBoxingElimination()) {
            operationNodeGen.add(new LoadLocalDataFactory().create());
            operationNodeGen.add(new StoreLocalDataFactory().create());
        }
        // Define a static singleton object for instructions that don't have any data.
        operationNodeGen.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), context.getType(Object.class), "EPSILON = new Object()"));

        // Define the classes that implement continuations (yield).
        if (model.enableYield) {
            operationNodeGen.add(new ContinuationRootFactory().create());
            operationNodeGen.add(new ContinuationLocationImplFactory().create());
        }

        // Define a static block with any necessary initialization code.
        operationNodeGen.add(createStaticConstructor());

        // Define the root node's constructors.
        operationNodeGen.add(createFrameDescriptorConstructor());
        operationNodeGen.add(createFrameDescriptorBuliderConstructor());

        // Define the execute method.
        operationNodeGen.add(createExecute());

        // Define a continueAt method.
        // This method delegates to the current interpreter's continueAt, handling the case where
        // the interpreter changes itself to another one (e.g., Uncached becomes Cached because the
        // method is hot).
        operationNodeGen.add(createContinueAt());

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
        operationNodeGen.add(compFinal(1, new CodeVariableElement(Set.of(PRIVATE), context.getType(Object[].class), "objs")));
        operationNodeGen.add(compFinal(1, new CodeVariableElement(Set.of(PRIVATE), context.getType(int[].class), "handlers")));
        operationNodeGen.add(compFinal(1, new CodeVariableElement(Set.of(PRIVATE), context.getType(int[].class), "sourceInfo")));
        operationNodeGen.add(compFinal(new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "numLocals")));
        operationNodeGen.add(compFinal(new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "buildIndex")));
        if (model.hasBoxingElimination()) {
            operationNodeGen.add(compFinal(1, new CodeVariableElement(Set.of(PRIVATE), context.getType(byte[].class), "localBoxingState")));
        }
        if (model.generateUncached) {
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

            CodeTypeElement el = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, instr.getInternalName() + "Gen");
            el.setSuperClass(types.Node);
            factory.create(el);
            new CustomInstructionPostProcessor().process(el, instr);

            nodeConsts.prependToClass(el);
            operationNodeGen.add(el);
        }

        consts.addElementsTo(operationNodeGen);

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

        b.statement("int i = 0;");

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

        b.declaration(operationNodeGen.asType(), "clone", "(" + operationNodeGen.getSimpleName() + ") this.copy()");

        b.statement("clone.interpreter = " + (model.generateUncached ? "UN" : "") + "CACHED_INTERPRETER");
        b.statement("clone.objs = new Object[objs.length]");

        b.startFor().string("int bci = 0; bci < bc.length; bci++").end().startBlock();

        b.startSwitch().string("bc[bci]").end().startBlock();

        for (InstructionModel instr : model.getInstructions()) {
            b.startCase().tree(createInstructionConstant(instr)).end().startBlock();

            switch (instr.kind) {
                case CUSTOM:
                case CUSTOM_SHORT_CIRCUIT:
                    String udName = instr.getInternalName() + "Gen" + (model.generateUncached && instr.needsUncachedData() ? "_UncachedData" : "");
                    b.declaration(udName, "curData", "(" + udName + ") objs[bci]");
                    b.declaration(udName, "newData", "new " + udName + "()");

                    for (InstructionField field : instr.getUncachedFields()) {
                        b.statement("newData." + field.name + " = curData." + field.name);
                    }

                    b.statement("clone.objs[bci] = clone.insert(newData)");

                    break;
                default:
                    b.statement("assert !(this.objs[bci] instanceof Node)");
                    b.statement("clone.objs[bci] = this.objs[bci]");
                    break;
            }

            b.statement("break");
            b.end();
        }

        b.end();

        b.end();

        b.startReturn().string("clone").end();

        return ex;
    }

    private CodeVariableElement createInterpreterField() {
        CodeVariableElement fld = new CodeVariableElement(Set.of(PRIVATE), baseInterpreter.asType(), "interpreter");
        fld = compFinal(fld);

        if (model.generateUncached) {
            fld.createInitBuilder().string("UNCACHED_INTERPRETER");
        } else {
            fld.createInitBuilder().string("CACHED_INTERPRETER");
        }

        return fld;
    }

    private static CodeVariableElement createInterpreterVariantField(CodeTypeElement interpreterType, String name) {
        CodeVariableElement fld = new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), interpreterType.asType(), name + "_INTERPRETER");
        fld.createInitBuilder().startNew(interpreterType.asType()).end();
        return fld;
    }

    private CodeExecutableElement createStaticConstructor() {
        CodeExecutableElement ctor = new CodeExecutableElement(Set.of(STATIC), null, "<cinit>");
        CodeTreeBuilder b = ctor.createBuilder();

        if (model.enableTracing) {
            b.startStatement().startStaticCall(types.ExecutionTracer, "initialize");
            b.typeLiteral(model.templateType.asType());
            b.doubleQuote(model.decisionsFilePath);

            b.startNewArray(arrayOf(context.getType(String.class)), null);
            b.string("null");
            for (InstructionModel instruction : model.getInstructions()) {
                b.doubleQuote(instruction.name);
            }
            b.end();

            b.startNewArray(arrayOf(context.getType(String[].class)), null);
            b.string("null");
            for (InstructionModel instruction : model.getInstructions()) {
                if (instruction.kind == InstructionKind.CUSTOM || instruction.kind == InstructionKind.CUSTOM_SHORT_CIRCUIT) {
                    b.startNewArray(arrayOf(context.getType(String.class)), null);
                    for (SpecializationData spec : instruction.nodeData.getSpecializations()) {
                        b.doubleQuote(spec.getId());
                    }
                    b.end();
                } else {
                    b.string("null");
                }
            }
            b.end();

            b.end(2);
        }

        return ctor;
    }

    private CodeExecutableElement createFrameDescriptorConstructor() {
        CodeExecutableElement ctor = GeneratorUtils.createSuperConstructor(operationNodeGen, model.fdConstructor);
        ctor.getModifiers().clear();
        ctor.getModifiers().add(PRIVATE);
        return ctor;
    }

    private CodeExecutableElement createFrameDescriptorBuliderConstructor() {
        CodeExecutableElement ctor;
        if (model.fdBuilderConstructor == null) {
            ctor = new CodeExecutableElement(Set.of(PRIVATE), null, operationNodeGen.getSimpleName().toString());
            ctor.addParameter(new CodeVariableElement(types.TruffleLanguage, "language"));
            ctor.addParameter(new CodeVariableElement(new GeneratedTypeMirror("", "FrameDescriptor.Builder"), "builder"));
            ctor.createBuilder().statement("this(language, builder.build())");
        } else {
            ctor = GeneratorUtils.createSuperConstructor(operationNodeGen, model.fdBuilderConstructor);
            ctor.getModifiers().clear();
            ctor.getModifiers().add(PRIVATE);
        }

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

        b.startWhile().string("true").end().startBlock();
        b.startAssign("state").startCall("interpreter.continueAt");
        b.string("this, frame");
        if (model.enableYield) {
            b.string("generatorFrame");
        }
        b.string("bc, objs, handlers, state, numLocals");
        if (model.hasBoxingElimination()) {
            b.string("localBoxingState");
        }
        b.end(2);
        b.startIf().string("(state & 0xffff) == 0xffff").end().startBlock();
        b.statement("break");
        b.end().startElseBlock();
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
        b.startAssign("Builder builder").startNew(builder.asType());
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

        b.statement("Object[] instructions = new Object[bc.length]");

        b.startFor().string("int bci = 0; bci < bc.length; bci++").end().startBlock();

        b.startSwitch().string("bc[bci]").end().startBlock();

        for (InstructionModel instr : model.getInstructions()) {
            b.startCase().tree(createInstructionConstant(instr)).end().startBlock();
            b.statement("Object data = objs[bci]");
            b.startAssign("instructions[bci]").startNewArray(arrayOf(context.getType(Object.class)), null);
            b.string("bci");
            b.doubleQuote(instr.name);
            b.string("new short[] {" + instr.id + "}");

            b.startNewArray(arrayOf(context.getType(Object.class)), null);

            switch (instr.kind) {
                case BRANCH:
                case BRANCH_FALSE:
                    buildIntrospectionArgument(b, "BRANCH_OFFSET", "((IntRef) data).value");
                    break;
                case LOAD_CONSTANT:
                    buildIntrospectionArgument(b, "CONSTANT", "data");
                    break;
                case LOAD_ARGUMENT:
                    buildIntrospectionArgument(b, "ARGUMENT", "data");
                    break;
                case LOAD_LOCAL:
                    if (model.hasBoxingElimination()) {
                        buildIntrospectionArgument(b, "LOCAL", "(int) ((LoadLocalData) data).v_index");
                        break;
                    }
                    // fall-through
                case STORE_LOCAL:
                    if (model.hasBoxingElimination()) {
                        buildIntrospectionArgument(b, "LOCAL", "(int) ((StoreLocalData) data).s_index");
                        break;
                    }
                    // fall-through
                case LOAD_LOCAL_MATERIALIZED:
                case STORE_LOCAL_MATERIALIZED:
                case THROW:
                    buildIntrospectionArgument(b, "LOCAL", "((IntRef) data).value");
                    break;
                case CUSTOM:
                    break;
                case CUSTOM_SHORT_CIRCUIT:
                    buildIntrospectionArgument(b, "BRANCH_OFFSET", "((" + instr.getInternalName() + "Gen" + (model.generateUncached ? "_UncachedData" : "") + " ) data).op_branchTarget_.value");
                    break;
            }

            b.end();

            b.end(2);
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
        b.string("new Object[]{0, instructions, exHandlersInfo, null}");
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

        ex.addAnnotationMirror(new CodeAnnotationMirror(types.ExplodeLoop));

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

        ex.addParameter(new CodeVariableElement(baseInterpreter.asType(), "toInterpreter"));

        CodeTreeBuilder b = ex.createBuilder();

        b.startIf().string("toInterpreter == interpreter").end().startBlock();
        b.returnStatement();
        b.end();

        b.startIf().string("toInterpreter == CACHED_INTERPRETER && interpreter == INSTRUMENTABLE_INTERPRETER").end().startBlock();
        b.returnStatement();
        b.end();

        b.statement("Object[] newObjs = new Object[bc.length]");

        b.startFor().string("int bci = 0; bci < bc.length; bci++").end().startBlock();

        b.startSwitch().string("bc[bci]").end().startBlock();

        for (InstructionModel instr : model.getInstructions()) {
            switch (instr.kind) {
                case CUSTOM:
                case CUSTOM_SHORT_CIRCUIT:
                    break;
                default:
                    continue;
            }

            b.startCase().tree(createInstructionConstant(instr)).end().startBlock();

            switch (instr.kind) {
                case CUSTOM:
                case CUSTOM_SHORT_CIRCUIT:
                    b.statement(instr.getInternalName() + "Gen data = new " + instr.getInternalName() + "Gen()");
                    if (model.generateUncached && instr.needsUncachedData()) {
                        b.startIf().string("interpreter == UNCACHED_INTERPRETER").end().startBlock();

                        b.statement(instr.getInternalName() + "Gen_UncachedData oldData = (" + instr.getInternalName() + "Gen_UncachedData) objs[bci]");
                        for (InstructionField field : instr.getUncachedFields()) {
                            b.statement("data." + field.name + " = oldData." + field.name);
                        }

                        // todo: initialize cached fields
                        b.end();
                    }

                    b.statement("newObjs[bci] = insert(data)");
                    break;

                default:
                    throw new AssertionError();
            }

            b.statement("break");
            b.end();
        }

        b.caseDefault().startBlock();
        b.statement("newObjs[bci] = objs[bci]");
        b.statement("break");
        b.end();

        b.end(); // } switch

        b.end(); // } for

        if (model.hasBoxingElimination() && model.generateUncached) {
            b.startIf().string("interpreter == UNCACHED_INTERPRETER").end().startBlock();
            b.statement("localBoxingState = new byte[numLocals]");
            b.end();
        }

        b.statement("objs = newObjs");
        b.statement("interpreter = toInterpreter");

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

        private CodeTypeElement deserializerContextImpl = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "DeserializerContextImpl");

        CodeTypeElement savedState = new CodeTypeElement(Set.of(PRIVATE, STATIC), ElementKind.CLASS, null, "SavedState");
        CodeTypeElement finallyTryContext = new CodeTypeElement(Set.of(PRIVATE, STATIC), ElementKind.CLASS, null, "FinallyTryContext");

        // this is per-function state that needs to be stored/restored when going into/out of
        // functions
        List<CodeVariableElement> builderState = new ArrayList<>(List.of(
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(short[].class), "bc"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "bci"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(Object[].class), "objs"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int[].class), "operationStack"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int[].class), "operationStartSpStack"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(Object[].class), "operationData"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int[].class), "operationChildCount"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int[].class), "opSeqNumStack"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "operationSp"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "numLocals"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "maxStack"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "curStack"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int[].class), "exHandlers"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "exHandlerCount"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int[].class), "stackValueBciStack"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "stackValueBciSp"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int[].class), "sourceIndexStack"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "sourceIndexSp"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int[].class), "sourceLocationStack"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "sourceLocationSp"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "opSeqNum"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int[].class), "sourceInfo"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "sourceInfoIndex"),
                        new CodeVariableElement(Set.of(PRIVATE), finallyTryContext.asType(), "finallyTryContext"),

                        // must be last
                        new CodeVariableElement(Set.of(PRIVATE), savedState.asType(), "savedState")));

        {
            if (model.enableTracing) {
                builderState.add(0, new CodeVariableElement(Set.of(PRIVATE), context.getType(boolean[].class), "basicBlockBoundary"));
            }

            if (model.enableYield) {
                builderState.add(0, new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "numYields"));
            }
        }

        class SavedStateFactory {
            private CodeTypeElement create() {
                savedState.addAll(builderState);
                savedState.add(createConstructorUsingFields(Set.of(), savedState, null));

                return savedState;
            }
        }

        class FinallyTryContextFactory {
            private CodeTypeElement create() {
                if (model.enableTracing) {
                    finallyTryContext.add(new CodeVariableElement(context.getType(boolean[].class), "basicBlockBoundary"));
                }
                finallyTryContext.addAll(List.of(
                                new CodeVariableElement(context.getType(short[].class), "bc"),
                                new CodeVariableElement(context.getType(int.class), "bci"),
                                new CodeVariableElement(context.getType(Object[].class), "objs"),
                                new CodeVariableElement(context.getType(int.class), "curStack"),
                                new CodeVariableElement(context.getType(int.class), "maxStack"),
                                new CodeVariableElement(context.getType(int[].class), "sourceInfo"),
                                new CodeVariableElement(context.getType(int.class), "sourceInfoIndex"),
                                new CodeVariableElement(context.getType(int[].class), "exHandlers"),
                                new CodeVariableElement(context.getType(int.class), "exHandlerCount"),
                                new CodeVariableElement(context.getType(int.class), "finallyTrySequenceNumber"),
                                new CodeVariableElement(generic(context.getDeclaredType(HashSet.class), intRef.asType()), "outerReferences"),
                                new CodeVariableElement(finallyTryContext.asType(), "finallyTryContext")));

                finallyTryContext.add(createConstructorUsingFields(Set.of(), finallyTryContext, null));

                // these could be merged with their counterparts above
                finallyTryContext.addAll(List.of(
                                new CodeVariableElement(context.getType(short[].class), "handlerBc"),
                                new CodeVariableElement(context.getType(Object[].class), "handlerObjs"),
                                new CodeVariableElement(context.getType(int.class), "handlerMaxStack"),
                                new CodeVariableElement(context.getType(int[].class), "handlerSourceInfo"),
                                new CodeVariableElement(context.getType(int[].class), "handlerExHandlers")));

                if (model.enableTracing) {
                    finallyTryContext.add(new CodeVariableElement(context.getType(boolean[].class), "handlerBasicBlockBoundary"));
                }

                return finallyTryContext;
            }
        }

        class DeserializerContextImplFactory {
            private CodeTypeElement create() {
                deserializerContextImpl.setEnclosingElement(operationNodeGen);
                deserializerContextImpl.getImplements().add(types.OperationDeserializer_DeserializerContext);

                deserializerContextImpl.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), generic(context.getDeclaredType(ArrayList.class), operationNodeGen.asType()), "builtNodes"));
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

            builder.add(new SavedStateFactory().create());
            builder.add(new FinallyTryContextFactory().create());

            builder.add(createOperationNames());

            builder.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), operationNodesImpl.asType(), "nodes"));
            builder.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), context.getType(boolean.class), "isReparse"));
            builder.add(new CodeVariableElement(Set.of(PRIVATE), context.getType(boolean.class), "withSource"));
            builder.add(new CodeVariableElement(Set.of(PRIVATE), context.getType(boolean.class), "withInstrumentation"));
            builder.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), generic(context.getDeclaredType(ArrayList.class), operationNodeGen.asType()), "builtNodes"));
            builder.add(new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "buildIndex"));
            builder.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), generic(context.getDeclaredType(ArrayList.class), types.Source), "sources"));

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

            for (OperationModel operation : model.getOperations()) {
                if (operation.hasChildren()) {
                    builder.add(createBegin(operation));
                    builder.add(createEnd(operation));
                } else {
                    builder.add(createEmit(operation));
                }
            }

            builder.add(createBeginHelper());
            builder.add(createEndHelper());
            builder.add(createEmitHelperBegin());
            builder.add(createBeforeChild());
            builder.add(createAfterChild());
            builder.add(createDoEmitFinallyHandler());
            builder.add(createDoEmitInstruction());
            builder.add(createDoEmitVariadic());
            builder.add(createDoCreateExceptionHandler());
            builder.add(createDoEmitSourceInfo());
            builder.add(createFinish());
            builder.add(createDoEmitLeaves());
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

            b.startReturn().startNew(operationLocalImpl.asType()).startNew(intRef.asType()).string("numLocals++").end(3);

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
                b.startNew(intRef.asType()).string("-1").end();
                b.string(serialization.getName(), ".", serializationElements.labelCount.getName(), "++");
                b.string("0");
                b.end(2);
                b.end();
            }

            b.startIf();
            b.string("operationSp == 0 || (operationStack[operationSp - 1] != ").tree(createOperationConstant(model.blockOperation));
            b.string(" && operationStack[operationSp - 1] != ").tree(createOperationConstant(model.rootOperation)).string(")");
            b.end().startBlock();
            buildThrowIllegalStateException(b, "\"Labels must be created inside either Block or Root operations.\"");
            b.end();

            b.startReturn().startNew(operationLabelImpl.asType());
            b.startNew(intRef.asType()).string("-1").end();
            b.string("opSeqNumStack[operationSp - 1]");
            b.string("finallyTryContext == null ? -1 : finallyTryContext.finallyTrySequenceNumber");
            b.end(2);

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

        private CodeExecutableElement createBeginHelper() {
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
            b.startAssign("operationStartSpStack").startStaticCall(context.getType(Arrays.class), "copyOf");
            b.string("operationStartSpStack");
            b.string("operationStartSpStack.length * 2");
            b.end(2);
            b.startAssign("operationChildCount").startStaticCall(context.getType(Arrays.class), "copyOf");
            b.string("operationChildCount");
            b.string("operationChildCount.length * 2");
            b.end(2);
            b.startAssign("operationData").startStaticCall(context.getType(Arrays.class), "copyOf");
            b.string("operationData");
            b.string("operationData.length * 2");
            b.end(2);
            b.startAssign("opSeqNumStack").startStaticCall(context.getType(Arrays.class), "copyOf");
            b.string("opSeqNumStack");
            b.string("opSeqNumStack.length * 2");
            b.end(2);
            b.end(); // }

            b.statement("operationStack[operationSp] = id");
            b.statement("operationChildCount[operationSp] = 0");
            b.statement("operationData[operationSp] = data");
            b.statement("operationStartSpStack[operationSp] = curStack");
            b.statement("opSeqNumStack[operationSp++] = opSeqNum++");

            return ex;
        }

        private CodeExecutableElement createBegin(OperationModel operation) {
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

            if (operation.kind == OperationKind.ROOT) {
                b.startIf().string("bc != null").end().startBlock(); // {
                b.startAssign("savedState").startNew(savedState.asType());
                b.variables(builderState);
                b.end(2);
                b.end(); // }

                b.statement("bc = new short[32]");
                b.statement("bci = 0");
                b.statement("objs = new Object[32]");
                if (model.enableTracing) {
                    b.statement("basicBlockBoundary = new boolean[33]");
                }
                b.statement("operationStack = new int[8]");
                b.statement("operationData = new Object[8]");
                b.statement("operationStartSpStack = new int[8]");
                b.statement("operationChildCount = new int[8]");
                b.statement("opSeqNumStack = new int[8]");
                b.statement("opSeqNum = 0");
                b.statement("operationSp = 0");
                b.statement("numLocals = 0");
                b.statement("curStack = 0");
                b.statement("maxStack = 0");
                b.statement("exHandlers = new int[10]");
                b.statement("exHandlerCount = 0");

                if (model.hasBoxingElimination()) {
                    b.statement("stackValueBciStack = new int[8]");
                    b.statement("stackValueBciSp = 0");
                }

                b.startIf().string("withSource").end().startBlock();
                b.statement("sourceIndexStack = new int[1]");
                b.statement("sourceIndexSp = 0");
                b.statement("sourceLocationStack = new int[12]");
                b.statement("sourceLocationSp = 0");
                b.statement("sourceInfo = new int[15]");
                b.statement("sourceInfoIndex = 0");
                b.end();
            } else {
                b.startStatement().startCall("beforeChild").end(2);
            }

            b.startStatement().startCall("beginOperation");
            b.tree(createOperationConstant(operation));

            buildOperationBeginData(b, operation);
            b.end(2);

            switch (operation.kind) {
                case TRY_CATCH:
                    b.startBlock();
                    b.statement("Object[] data = (Object[]) operationData[operationSp - 1]");
                    b.statement("data[0] = bci");
                    b.statement("data[3] = curStack");
                    b.statement("data[4] = arg0");
                    b.end();
                    break;
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
                    if (model.enableTracing) {
                        b.string("basicBlockBoundary");
                    }
                    b.string("bc");
                    b.string("bci");
                    b.string("objs");
                    b.string("curStack");
                    b.string("maxStack");
                    b.string("sourceInfo");
                    b.string("sourceInfoIndex");
                    b.string("exHandlers");
                    b.string("exHandlerCount");
                    b.string("opSeqNum - 1");
                    b.string("new HashSet<>()");
                    b.string("finallyTryContext");
                    b.end(2);

                    b.statement("bc = new short[16]");
                    b.statement("bci = 0");
                    b.statement("objs = new Object[16]");
                    b.statement("curStack = 0");
                    b.statement("maxStack = 0");
                    if (model.enableTracing) {
                        b.statement("basicBlockBoundary = new boolean[17]");
                        b.statement("basicBlockBoundary[0] = true");
                    }

                    b.startIf().string("withSource").end().startBlock();
                    b.statement("sourceInfo = new int[15]");
                    b.statement("sourceInfoIndex = 0");
                    b.end();

                    b.statement("exHandlers = new int[10]");
                    b.statement("exHandlerCount = 0");

                    break;
            }

            return ex;
        }

        private void createSerializeBegin(OperationModel operation, CodeTreeBuilder b) {
            serializationWrapException(b, () -> {

                CodeTreeBuilder after = CodeTreeBuilder.createBuilder();
                int i = 0;
                for (TypeMirror argType : operation.operationArguments) {
                    if (ElementUtils.typeEquals(argType, types.TruffleLanguage)) {
                        b.statement("serialization.language = arg" + i);
                    } else if (ElementUtils.typeEquals(argType, types.OperationLocal)) {

                        serializationElements.writeShort(after, "(short) ((OperationLocalImpl) arg" + i + ").index.value");

                    } else if (ElementUtils.typeEquals(argType, new ArrayCodeTypeMirror(types.OperationLocal))) {

                        serializationElements.writeShort(after, "(short) arg" + i + ".length");
                        after.startFor().string("int i = 0; i < arg" + i + ".length; i++").end().startBlock();
                        serializationElements.writeShort(after, "(short) ((OperationLocalImpl) arg" + i + "[i]).index.value");
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
                case ROOT:
                    b.string("new Object[]{false, arg0}");
                    break;
                case BLOCK:
                case INSTRUMENT_TAG:
                case SOURCE:
                case SOURCE_SECTION:
                    b.string("new Object[]{false}");
                    break;
                case IF_THEN:
                    b.string("new IntRef()");
                    break;
                case IF_THEN_ELSE:
                case CONDITIONAL:
                case WHILE:
                    b.string("new IntRef[]{new IntRef(bci), new IntRef()}");
                    break;
                case STORE_LOCAL:
                case STORE_LOCAL_MATERIALIZED:
                case LOAD_LOCAL_MATERIALIZED:
                    b.string("arg0");
                    break;
                case CUSTOM_SIMPLE:
                case CUSTOM_SHORT_CIRCUIT:
                    b.startNewArray(arrayOf(context.getType(Object.class)), null);
                    if (operation.kind == OperationKind.CUSTOM_SHORT_CIRCUIT) {
                        b.string("new IntRef()");
                    }

                    for (int i = 0; i < operation.operationArguments.length; i++) {
                        b.string("arg" + i);
                    }

                    b.end();
                    break;
                case TRY_CATCH:
                    b.string("new Object[6]");
                    break;
                default:
                    b.string("null");
                    break;
            }
        }

        private CodeExecutableElement createEndHelper() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "endOperation");
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "id"));

            CodeTreeBuilder b = ex.createBuilder();

            b.startIf().string("operationSp <= 0 || operationStack == null").end().startBlock(); // {
            b.startThrow().startNew(context.getType(IllegalStateException.class));
            b.string("\"Unexpected operation end - there are no operations on the stack. Did you forget a beginRoot()?\"").end();
            b.end(2);
            b.end(); // }

            b.startIf().string("operationStack[operationSp - 1] != id").end().startBlock(); // {
            b.startThrow().startNew(context.getType(IllegalStateException.class));
            b.string("\"Unexpected operation end, expected end\" + OPERATION_NAMES[operationStack[operationSp - 1]] + \", but got end \" + OPERATION_NAMES[id]").end();
            b.end(2);
            b.end(); // }

            b.statement("operationSp -= 1");

            return ex;
        }

        private Element createEnd(OperationModel operation) {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), context.getType(void.class), "end" + operation.name);
            CodeTreeBuilder b = ex.createBuilder();

            if (model.enableSerialization) {
                b.startIf().string("serialization != null").end().startBlock();
                serializationWrapException(b, () -> {

                    if (operation.kind == OperationKind.ROOT) {
                        b.startStatement();
                        b.type(operationNodeGen.asType()).string(" node = ").startNew(operationNodeGen.asType()).string("serialization.language").string("FrameDescriptor.newBuilder()").end();
                        b.end();
                        b.statement("node.buildIndex = buildIndex++");
                        serializationElements.writeShort(b, serializationElements.codeEnd[operation.id]);
                        b.statement("builtNodes.add(node)");
                        b.statement("return node");
                    } else {
                        serializationElements.writeShort(b, serializationElements.codeEnd[operation.id]);
                        b.statement("return");
                    }

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

            if (operation.isVariadic && operation.numChildren > 1) {
                b.startIf().string("operationChildCount[operationSp] < " + (operation.numChildren - 1)).end().startBlock();
                buildThrowIllegalStateException(b, "\"Operation " + operation.name + " expected at least " + (operation.numChildren - 1) +
                                " children, but \" + operationChildCount[operationSp] + \" provided. This is probably a bug in the parser.\"");
                b.end();
            } else if (!operation.isVariadic) {
                b.startIf().string("operationChildCount[operationSp] != " + operation.numChildren).end().startBlock();
                buildThrowIllegalStateException(b, "\"Operation " + operation.name + " expected exactly " + operation.numChildren +
                                " children, but \" + operationChildCount[operationSp] + \" provided. This is probably a bug in the parser.\"");
                b.end();
            }

            if (operation.kind == OperationKind.ROOT) {
                ex.setReturnType(model.templateType.asType());

                b.declaration(types.TruffleLanguage, "language");

                b.startAssign("language").cast(types.TruffleLanguage).string("((Object[]) operationData[operationSp])[1]").end();

                b.declaration(operationNodeGen.asType(), "result", (CodeTree) null);
                b.startIf().string("isReparse").end().startBlock(); // {
                b.statement("result = builtNodes.get(buildIndex)");

                b.startAssert().string("result.buildIndex == buildIndex").end();

                b.end().startElseBlock(); // } {

                b.declaration(types.FrameDescriptor, ".Builder fdb", "FrameDescriptor.newBuilder(numLocals + maxStack)");

                b.startStatement().startCall("fdb.addSlots");
                b.string("numLocals + maxStack");
                b.staticReference(types.FrameSlotKind, "Illegal");
                b.end(2);

                b.startAssign("result").startNew(operationNodeGen.asType()).string("language").string("fdb").end(2);

                b.startAssign("result.nodes").string("nodes").end();
                b.startAssign("result.bc").string("Arrays.copyOf(bc, bci)").end();
                b.startAssign("result.objs").string("Arrays.copyOf(objs, bci)").end();
                if (model.enableTracing) {
                    b.startAssign("result.basicBlockBoundary").string("Arrays.copyOf(basicBlockBoundary, bci)").end();
                }

                b.startFor().string("int i = 0; i < bci; i++").end().startBlock();

                b.startIf().string("objs[i] instanceof Node").end().startBlock();
                b.statement("result.insert((Node) objs[i])");
                b.end();

                if (model.enableYield) {
                    b.startElseIf().string("objs[i] instanceof ContinuationLocationImpl").end().startBlock();
                    b.statement("ContinuationLocationImpl cl = (ContinuationLocationImpl) objs[i]");
                    b.statement("cl.rootNode = new ContinuationRoot(language, result.getFrameDescriptor(), result, cl.target)");
                    b.end();
                }

                b.end();

                b.startAssign("result.handlers").string("Arrays.copyOf(exHandlers, exHandlerCount)").end();
                b.startAssign("result.numLocals").string("numLocals").end();
                b.startAssign("result.buildIndex").string("buildIndex").end();

                if (model.hasBoxingElimination() && !model.generateUncached) {
                    // need to initialize it now
                    b.startAssign("result.localBoxingState").string("new byte[numLocals]").end();
                }

                b.startAssert().string("builtNodes.size() == buildIndex").end();
                b.statement("builtNodes.add(result)");

                b.end(); // }

                b.statement("buildIndex++");

                b.startIf().string("withSource").end().startBlock();
                b.statement("result.sourceInfo = Arrays.copyOf(sourceInfo, sourceInfoIndex)");
                b.end();

                b.startIf().string("savedState == null").end().startBlock(); // {
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

            switch (operation.kind) {
                case TRY_CATCH:
                    b.startBlock();
                    b.statement("Object[] data = (Object[])operationData[operationSp]");
                    b.statement("((IntRef) data[5]).value = bci");

                    // todo: ordering is bad, this should be moved to after the first child
                    b.statement("doCreateExceptionHandler((int) data[0], (int) data[1], (int) data[2], (int) data[3], ((OperationLocalImpl) data[4]).index.value)");
                    b.end();
                    break;
                case CUSTOM_SHORT_CIRCUIT:
                    if (model.enableTracing) {
                        b.statement("basicBlockBoundary[bci] = true");
                    }
                    b.statement("((IntRef) ((Object[]) operationData[operationSp])[0]).value = bci");
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
                    b.statement("FinallyTryContext ctx = (FinallyTryContext) operationData[operationSp]");
                    b.statement("int exceptionLocal = numLocals++");
                    b.statement("int exHandlerIndex = doCreateExceptionHandler(ctx.bci, bci, -1, curStack, exceptionLocal)");

                    b.statement("doEmitFinallyHandler(ctx)");

                    b.statement("IntRef endBranch = new IntRef(-1)");
                    buildEmitInstruction(b, model.branchInstruction, "endBranch");

                    // set handlerBci for the exception handler
                    b.statement("exHandlers[exHandlerIndex + 2] = bci");

                    b.statement("doEmitFinallyHandler(ctx)");

                    buildEmitInstruction(b, model.throwInstruction, "new IntRef(exceptionLocal)");

                    b.statement("endBranch.value = bci");

                    break;
                case FINALLY_TRY_NO_EXCEPT:
                    b.statement("FinallyTryContext ctx = (FinallyTryContext) operationData[operationSp]");
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
                b.string("(boolean) ((Object[]) operationData[operationSp])[0]");
            } else {
                b.string("" + !operation.isVoid);
            }
            b.end(2);

            return ex;
        }

        private void buildEmitOperationInstruction(CodeTreeBuilder b, OperationModel operation) {
            b.startBlock();
            switch (operation.kind) {
                case STORE_LOCAL:
                    if (model.hasBoxingElimination()) {
                        b.statement("StoreLocalData argument = new StoreLocalData((short) ((OperationLocalImpl) operationData[operationSp]).index.value)");
                    } else {
                        b.statement("IntRef argument = ((OperationLocalImpl) operationData[operationSp]).index");
                    }
                    break;
                case STORE_LOCAL_MATERIALIZED:
                case LOAD_LOCAL_MATERIALIZED:
                    b.statement("IntRef argument = ((OperationLocalImpl) operationData[operationSp]).index");
                    break;
                case RETURN:
                    b.statement("Object argument = EPSILON");
                    break;
                case LOAD_ARGUMENT:
                case LOAD_CONSTANT:
                    b.statement("Object argument = arg0");
                    break;
                case LOAD_LOCAL:
                    if (model.hasBoxingElimination()) {
                        b.statement("LoadLocalData argument = new LoadLocalData((short) ((OperationLocalImpl) arg0).index.value)");
                    } else {
                        b.statement("IntRef argument = ((OperationLocalImpl) arg0).index");
                    }
                    break;
                case BRANCH:
                    b.statement("IntRef argument = ((OperationLabelImpl) arg0).index");
                    break;
                case CUSTOM_SIMPLE:
                case CUSTOM_SHORT_CIRCUIT:
                    buildCustomInitializer(b, operation, operation.instruction);
                    break;
                case YIELD:
                    b.statement("ContinuationLocationImpl argument = new ContinuationLocationImpl(numYields++, (curStack << 16) | (bci + 1))");
                    break;
                default:
                    b.statement("/* TODO: NOT IMPLEMENTED */");
                    break;
            }

            buildEmitInstruction(b, operation.instruction, "argument");
            b.end();
        }

        private CodeExecutableElement createEmitHelperBegin() {
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

            switch (operation.kind) {
                case LABEL:
                    b.startAssign("OperationLabelImpl lbl").string("(OperationLabelImpl) arg0").end();

                    b.startIf().string("lbl.index.value != -1").end().startBlock();
                    buildThrowIllegalStateException(b, "\"OperationLabel already emitted. Each label must be emitted exactly once.\"");
                    b.end();

                    b.startIf().string("lbl.declaringOp != opSeqNumStack[operationSp - 1]").end().startBlock();
                    buildThrowIllegalStateException(b, "\"OperationLabel must be emitted inside the same operation it was created in.\"");
                    b.end();

                    b.statement("((OperationLabelImpl) arg0).index.value = bci");
                    break;
                case BRANCH:
                    b.startAssign("OperationLabelImpl lbl").string("(OperationLabelImpl) arg0").end();

                    b.statement("boolean isFound = false");
                    b.startFor().string("int i = 0; i < operationSp; i++").end().startBlock();
                    b.startIf().string("opSeqNumStack[i] == lbl.declaringOp").end().startBlock();
                    b.statement("isFound = true");
                    b.statement("break");
                    b.end();
                    b.end();

                    b.startIf().string("!isFound").end().startBlock();
                    buildThrowIllegalStateException(b, "\"Branch must be targeting a label that is declared in an enclosing operation. Jumps into other operations are not permitted.\"");
                    b.end();

                    b.startIf().string("finallyTryContext != null && lbl.finallyTryOp != finallyTryContext.finallyTrySequenceNumber").end().startBlock();
                    b.statement("finallyTryContext.outerReferences.add(lbl.index)");
                    b.end();

                    b.statement("doEmitLeaves(lbl.declaringOp)");
                    break;
            }

            if (operation.instruction != null) {
                buildEmitOperationInstruction(b, operation);
            }

            b.startStatement().startCall("afterChild");
            b.string("" + !operation.isVoid);
            b.end(2);

            return ex;
        }

        private void buildCustomInitializer(CodeTreeBuilder b, OperationModel operation, InstructionModel instruction) {
            if (instruction.signature.isVariadic) {
                b.statement("doEmitVariadic(operationChildCount[operationSp] - " + (instruction.signature.valueCount - 1) + ")");
            }

            if (model.generateUncached) {
                if (!instruction.needsUncachedData()) {
                    b.statement("Object argument = EPSILON");
                    return;
                }

                b.statement(instruction.getInternalName() + "Gen_UncachedData argument = new " + instruction.getInternalName() + "Gen_UncachedData()");

            } else {
                b.statement(instruction.getInternalName() + "Gen argument = new " + instruction.getInternalName() + "Gen()");
            }

            boolean inEmit = operation.numChildren == 0;

            int argBase;
            if (operation.kind == OperationKind.CUSTOM_SHORT_CIRCUIT) {
                b.statement("argument.op_branchTarget_ = (IntRef) ((Object[]) data)[0]");
                argBase = 1;
            } else {
                argBase = 0;
            }

            for (int i = 0; i < instruction.signature.localSetterCount; i++) {
                b.startAssign("argument.op_localSetter" + i + "_");
                b.startStaticCall(types.LocalSetter, "create");
                if (inEmit) {
                    b.string("((OperationLocalImpl)arg" + (argBase + i) + ").index.value");
                } else {
                    b.string("((IntRef)((OperationLocalImpl)((Object[]) operationData[operationSp])[" + (argBase + i) + "]).index).value");
                }
                b.end(2);

            }

            argBase += instruction.signature.localSetterCount;

            for (int i = 0; i < instruction.signature.localSetterRangeCount; i++) {
                b.startBlock();
                if (inEmit) {
                    b.statement("OperationLocal[] argg = arg" + (argBase + i));
                } else {
                    b.statement("OperationLocal[] argg = (OperationLocal[]) ((Object[]) operationData[operationSp])[" + (argBase + i) + "]");
                }
                b.statement("int[] indices = new int[argg.length]");

                b.startFor().string("int ix = 0; ix < indices.length; ix++").end().startBlock();
                b.startAssign("indices[ix]").string("((OperationLocalImpl) argg[ix]).index.value").end();
                b.end();

                b.startAssign("argument.op_localSetterRange" + i + "_");
                b.startStaticCall(types.LocalSetterRange, "create");
                b.string("indices");
                b.end(2);

                b.end();
            }

            argBase += instruction.signature.localSetterRangeCount;

        }

        private CodeExecutableElement createBeforeChild() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "beforeChild");
            CodeTreeBuilder b = ex.createBuilder();

            createCheckRoot(b);

            b.statement("Object data = operationData[operationSp - 1]");
            b.statement("int childIndex = operationChildCount[operationSp - 1]");

            b.startSwitch().string("operationStack[operationSp - 1]").end().startBlock();

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
                    buildCustomInitializer(b, op, op.instruction);
                    buildEmitInstruction(b, op.instruction, "argument");
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

            b.statement("Object data = operationData[operationSp - 1]");
            b.statement("int childIndex = operationChildCount[operationSp - 1]");

            b.startSwitch().string("operationStack[operationSp - 1]").end().startBlock();

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
                            b.doubleQuote("Operation " + op.name + " expected a value-producing child at position " + i + ", but a void one was provided. This likely indicates a bug in the parser.");
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
                        buildEmitInstruction(b, model.branchFalseInstruction, "data");
                        b.end().startElseBlock();
                        b.statement("((IntRef) data).value = bci");
                        b.end();
                        if (model.enableTracing) {
                            b.statement("basicBlockBoundary[bci] = true");
                        }
                        break;
                    case CONDITIONAL:
                    case IF_THEN_ELSE:
                        b.startIf().string("childIndex == 0").end().startBlock();
                        buildEmitInstruction(b, model.branchFalseInstruction, "((IntRef[]) data)[0]");
                        b.end().startElseIf().string("childIndex == 1").end().startBlock();
                        buildEmitInstruction(b, model.branchInstruction, "((IntRef[]) data)[1]");
                        if (op.kind == OperationKind.CONDITIONAL) {
                            // we have to adjust the stack for the third child
                            b.statement("curStack -= 1");
                            if (model.hasBoxingElimination()) {
                                b.statement("stackValueBciSp -= 1");
                            }
                        }
                        b.statement("((IntRef[]) data)[0].value = bci");
                        b.end().startElseBlock();
                        b.statement("((IntRef[]) data)[1].value = bci");
                        b.end();
                        if (model.enableTracing) {
                            b.statement("basicBlockBoundary[bci] = true");
                        }
                        break;
                    case WHILE:
                        b.startIf().string("childIndex == 0").end().startBlock();
                        buildEmitInstruction(b, model.branchFalseInstruction, "((IntRef[]) data)[1]");
                        b.end().startElseBlock();
                        buildEmitInstruction(b, model.branchInstruction, "((IntRef[]) data)[0]");
                        b.statement("((IntRef[]) data)[1].value = bci");
                        b.end();
                        if (model.enableTracing) {
                            b.statement("basicBlockBoundary[bci] = true");
                        }
                        break;
                    case TRY_CATCH:
                        b.startIf().string("childIndex == 0").end().startBlock();
                        b.statement("Object[] dArray = (Object[]) data");
                        b.statement("dArray[1] = bci");
                        b.statement("dArray[5] = new IntRef()");
                        buildEmitInstruction(b, model.branchInstruction, "dArray[5]");
                        b.statement("dArray[2] = bci");
                        b.end();
                        if (model.enableTracing) {
                            b.statement("basicBlockBoundary[bci] = true");
                        }
                        break;
                    case FINALLY_TRY:
                    case FINALLY_TRY_NO_EXCEPT:
                        b.startIf().string("childIndex == 0").end().startBlock();
                        b.statement("finallyTryContext.handlerBc = Arrays.copyOf(bc, bci)");
                        b.statement("finallyTryContext.handlerObjs = Arrays.copyOf(objs, bci)");
                        b.statement("finallyTryContext.handlerMaxStack = maxStack");
                        if (model.enableTracing) {
                            b.statement("finallyTryContext.handlerBasicBlockBoundary = Arrays.copyOf(basicBlockBoundary, bci + 1)");
                        }
                        b.startIf().string("withSource").end().startBlock();
                        b.statement("finallyTryContext.handlerSourceInfo = Arrays.copyOf(sourceInfo, sourceInfoIndex)");
                        b.end();
                        b.statement("finallyTryContext.handlerExHandlers = Arrays.copyOf(exHandlers, exHandlerCount)");

                        b.statement("operationData[operationSp - 1] = finallyTryContext");
                        b.statement("bc = finallyTryContext.bc");
                        b.statement("bci = finallyTryContext.bci");
                        b.statement("objs = finallyTryContext.objs");
                        b.statement("curStack = finallyTryContext.curStack");
                        b.statement("maxStack = finallyTryContext.maxStack");
                        if (model.enableTracing) {
                            b.statement("basicBlockBoundary = finallyTryContext.basicBlockBoundary");
                        }
                        b.statement("sourceInfo = finallyTryContext.sourceInfo");
                        b.statement("sourceInfoIndex = finallyTryContext.sourceInfoIndex");
                        b.statement("exHandlers = finallyTryContext.exHandlers");
                        b.statement("exHandlerCount = finallyTryContext.exHandlerCount");
                        b.statement("finallyTryContext = finallyTryContext.finallyTryContext");
                        b.end();
                        break;
                }

                b.statement("break");
                b.end();
            }

            b.end();

            b.statement("operationChildCount[operationSp - 1] = childIndex + 1");

            return ex;
        }

        private void buildThrowIllegalStateException(CodeTreeBuilder b, String reasonCode) {
            b.startThrow().startNew(context.getType(IllegalStateException.class));
            if (reasonCode != null) {
                b.string(reasonCode);
            }
            b.end(2);
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
            b.statement("Object[] handlerObjs = context.handlerObjs");

            // b.statement("System.err.println(Arrays.toString(handlerBc))");

            // resize all arrays
            b.startIf().string("bci + handlerBc.length > bc.length").end().startBlock();
            buildCalculateNewLengthOfArray(b, "bc.length", "bci + handlerBc.length");

            b.statement("bc = Arrays.copyOf(bc, resultLength)");
            b.statement("objs = Arrays.copyOf(objs, resultLength)");
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
                b.statement("System.arraycopy(context.handlerBasicBlockBoundary, 0, basicBlockBoundary, bci, context.handlerBasicBlockBoundary.length)");
            }

            b.startFor().string("int idx = 0; idx < handlerBc.length; idx++").end().startBlock();
            b.startSwitch().string("handlerBc[idx]").end().startBlock();

            for (InstructionModel instr : model.getInstructions()) {
                switch (instr.kind) {
                    case BRANCH:
                    case BRANCH_FALSE:
                        b.startCase().tree(createInstructionConstant(instr)).end().startBlock();

                        b.startIf().string("context.outerReferences.contains(handlerObjs[idx])").end().startBlock();
                        b.statement("objs[offsetBci + idx] = handlerObjs[idx]");
                        b.end().startElseBlock();
                        b.startAssert().string("((IntRef) handlerObjs[idx]).value != -1").end();
                        b.statement("objs[offsetBci + idx] = new IntRef(((IntRef) handlerObjs[idx]).value + offsetBci)");
                        b.end();

                        b.statement("break");
                        b.end();
                        break;
                    case YIELD:
                        b.startCase().tree(createInstructionConstant(instr)).end().startBlock();

                        b.statement("ContinuationLocationImpl cl = (ContinuationLocationImpl) handlerObjs[idx];");
                        b.statement("assert (cl.target & 0xffff) == (idx + 1)");
                        b.statement("objs[offsetBci + idx] = new ContinuationLocationImpl(numYields++, cl.target + ((curStack << 16) | offsetBci))");

                        b.statement("break");
                        b.end();
                        break;
                    case CUSTOM:
                    case CUSTOM_SHORT_CIRCUIT:
                        b.startCase().tree(createInstructionConstant(instr)).end().startBlock();

                        if (model.generateUncached && !instr.needsUncachedData()) {
                            b.startAssert().string("handlerObjs[idx] == EPSILON").end();
                            b.statement("objs[offsetBci + idx] = EPSILON");
                        } else {
                            String dataClassName = instr.getInternalName() + "Gen" + (model.generateUncached ? "_UncachedData" : "");

                            b.statement(dataClassName + " curObj = (" + dataClassName + ") handlerObjs[idx]");
                            b.statement(dataClassName + " newObj = new " + dataClassName + "()");
                            b.statement("objs[offsetBci + idx] = newObj");

                            for (InstructionField field : instr.getUncachedFields()) {
                                b.startAssign("newObj." + field.name);
                                if (field.needLocationFixup) {
                                    if (ElementUtils.typeEquals(field.type, context.getType(int.class))) {
                                        b.string("curObj.", field.name, " + offsetBci");
                                    } else if (ElementUtils.typeEquals(field.type, new GeneratedTypeMirror("", "IntRef"))) {
                                        b.string("new IntRef(curObj.", field.name, ".value + offsetBci)");
                                    } else {
                                        throw new UnsupportedOperationException("how?");
                                    }
                                } else {
                                    b.string("curObj.", field.name);
                                }
                                b.end();
                            }
                        }

                        b.statement("break");
                        b.end();
                        break;
                }
            }

            b.caseDefault().startBlock();
            b.statement("objs[offsetBci + idx] = handlerObjs[idx]");
            b.statement("break");
            b.end();

            b.end();
            b.end();

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
            ex.addParameter(new CodeVariableElement(context.getType(Object.class), "data"));

            CodeTreeBuilder b = ex.createBuilder();

            b.startIf().string("bc.length == bci").end().startBlock(); // {
            b.startAssign("bc").startStaticCall(context.getType(Arrays.class), "copyOf");
            b.string("bc");
            b.string("bc.length * 2");
            b.end(2);
            b.startAssign("objs").startStaticCall(context.getType(Arrays.class), "copyOf");
            b.string("objs");
            b.string("bc.length * 2");
            b.end(2);
            if (model.enableTracing) {
                // since we can mark a start of the BB before it's first instruction is emitted,
                // basicBlockBoundary must always be at least 1 longer than `bc` array to prevent
                // ArrayIndexOutOfBoundsException
                b.startAssign("basicBlockBoundary").startStaticCall(context.getType(Arrays.class), "copyOf");
                b.string("basicBlockBoundary");
                b.string("(bc.length * 2) + 1");
                b.end(2);
            }
            b.end(); // }

            b.statement("bc[bci] = (short) instr");
            b.statement("objs[bci++] = data");

            return ex;
        }

        private CodeExecutableElement createDoEmitVariadic() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "doEmitVariadic");
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "count"));
            CodeTreeBuilder b = ex.createBuilder();

            int variadicCount = model.popVariadicInstruction.length - 1;

            b.startIf().string("count <= ").string(variadicCount).end().startBlock();
            b.startStatement().string("doEmitInstruction(").tree(createInstructionConstant(model.popVariadicInstruction[0])).string(" + count, EPSILON)").end();
            b.end().startElseBlock();

            b.startIf().string("curStack + 1 > maxStack").end().startBlock();
            b.statement("maxStack = curStack + 1");
            b.end();
            b.statement("int elementCount = count + 1");
            b.startStatement().string("doEmitInstruction(").tree(createInstructionConstant(model.storeNullInstruction)).string(", EPSILON)").end();

            b.startWhile().string("elementCount > 8").end().startBlock();
            b.startStatement().string("doEmitInstruction(").tree(createInstructionConstant(model.popVariadicInstruction[variadicCount])).string(", EPSILON)").end();
            b.statement("elementCount -= 7");
            b.end();

            b.startIf().string("elementCount > 0").end().startBlock();
            b.startStatement().string("doEmitInstruction(").tree(createInstructionConstant(model.popVariadicInstruction[0])).string(" + elementCount, EPSILON)").end();
            b.end();
            b.startStatement().string("doEmitInstruction(").tree(createInstructionConstant(model.mergeVariadicInstruction)).string(", EPSILON)").end();
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

        private void buildEmitInstruction(CodeTreeBuilder b, InstructionModel instr, String argument) {
            if (model.hasBoxingElimination()) {
                switch (instr.kind) {
                    case BRANCH:
                    case INSTRUMENTATION_ENTER:
                    case INSTRUMENTATION_EXIT:
                    case INSTRUMENTATION_LEAVE:
                    case RETURN:
                        break;
                    case BRANCH_FALSE:
                    case CUSTOM_SHORT_CIRCUIT:
                    case POP:
                        b.statement("stackValueBciSp--");
                        break;
                    case CUSTOM:
                    case CUSTOM_QUICKENED:
                        int effect;
                        if (instr.signature.isVariadic) {
                            b.statement("stackValueBciSp -= operationChildCount[operationSp] - " + (instr.signature.valueCount - 1));
                            effect = instr.signature.valueCount - 2;
                        } else {
                            effect = instr.signature.valueCount - 1;
                        }

                        for (int i = effect; i >= 0; i--) {
                            if (instr.signature.valueBoxingElimination[i]) {
                                b.statement(argument + ".op_childValue" + i + "_boxing_ = stackValueBciStack[--stackValueBciSp]");
                            } else {
                                b.statement("stackValueBciSp--");
                            }
                        }
                        if (!instr.signature.isVoid) {
                            buildPushStackIndex(b, instr.signature.resultBoxingElimination ? "0" : null, instr.signature.valueCount == 0);
                        }
                        break;
                    case LOAD_ARGUMENT:
                    case LOAD_CONSTANT:
                        buildPushStackIndex(b, null, true);
                        break;
                    case LOAD_LOCAL:
                        buildPushStackIndex(b, "0", true);
                        break;
                    case LOAD_LOCAL_MATERIALIZED:
                        b.statement("stackValueBciSp--");
                        buildPushStackIndex(b, null, true);
                        break;
                    case STORE_LOCAL:
                        if (model.hasBoxingElimination()) {
                            b.statement(argument + ".s_childIndex = stackValueBciStack[--stackValueBciSp]");
                        } else {
                            b.statement("stackValueBciSp--");
                        }
                        break;
                    case STORE_LOCAL_MATERIALIZED:
                        b.statement("stackValueBciSp -= 2");
                        break;
                    case THROW:
                        break;
                    case YIELD:
                        b.statement("stackValueBciSp--");
                        buildPushStackIndex(b, "0", false);
                        break;
                    default:
                        throw new UnsupportedOperationException();

                }
            }

            boolean hasPositiveDelta = false;

            switch (instr.kind) {
                case BRANCH:
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
            b.startGroup();
            if (argument != null) {
                b.string(argument);
            } else {
                b.string("EPSILON");
            }
            b.end();
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

            b.startIf().string("opSeqNumStack[i] == targetSeq").end().startBlock();
            b.returnStatement();
            b.end();

            b.startSwitch().string("operationStack[i]").end().startBlock();

            for (OperationModel op : model.getOperations()) {
                switch (op.kind) {
                    case FINALLY_TRY:
                    case FINALLY_TRY_NO_EXCEPT:
                        b.startCase().tree(createOperationConstant(op)).end().startBlock();

                        b.startIf().string("operationData[i] != null").end().startBlock();
                        b.statement("doEmitFinallyHandler((FinallyTryContext) operationData[i])");
                        b.end();

                        b.statement("break");
                        b.end();
                        break;
                }
            }

            b.end();

            b.end();

            return ex;
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

            b.statement("sources = withSource ? new ArrayList<>() : null");

            b.statement("this.builtNodes = new ArrayList<>()");

            return ctor;
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

    class BaseInterpreterFactory {
        private CodeTypeElement create() {
            baseInterpreter.add(createContinueAt());

            return baseInterpreter;
        }

        private CodeExecutableElement createContinueAt() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(ABSTRACT), context.getType(int.class), "continueAt");

            ex.addParameter(new CodeVariableElement(operationNodeGen.asType(), "$this"));
            ex.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));
            if (model.enableYield) {
                ex.addParameter(new CodeVariableElement(types.VirtualFrame, "generatorFrame"));
            }
            ex.addParameter(new CodeVariableElement(context.getType(short[].class), "bc"));
            ex.addParameter(new CodeVariableElement(context.getType(Object[].class), "objs"));
            ex.addParameter(new CodeVariableElement(context.getType(int[].class), "handlers"));
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "startState"));
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "numLocals"));
            if (model.hasBoxingElimination()) {
                ex.addParameter(new CodeVariableElement(context.getType(byte[].class), "localBoxingState"));
            }

            return ex;
        }
    }

    class InterpreterFactory {

        private CodeTypeElement interpreterType;
        private boolean isUncached;
        private boolean isInstrumented;

        InterpreterFactory(CodeTypeElement type, boolean isUncached, boolean isInstrumented) {
            this.interpreterType = type;
            this.isUncached = isUncached;
            this.isInstrumented = isInstrumented;
        }

        private CodeTypeElement create() {
            interpreterType.setSuperClass(baseInterpreter.asType());

            interpreterType.add(createContinueAt());

            if (!isUncached && model.hasBoxingElimination()) {
                interpreterType.add(createDoLoadLocalInitialize());
                interpreterType.add(createDoStoreLocalInitialize());
            }

            return interpreterType;
        }

        private CodeExecutableElement createContinueAt() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(baseInterpreter, "continueAt");
            CodeTreeBuilder b = ex.createBuilder();

            b.statement("int bci = startState & 0xffff");
            b.statement("int sp = (startState >> 16) & 0xffff");

            if (model.enableTracing) {
                b.declaration(context.getType(boolean[].class), "basicBlockBoundary", "$this.basicBlockBoundary");

                b.declaration(types.ExecutionTracer, "tracer");

                b.startAssign("tracer").startStaticCall(types.ExecutionTracer, "get");
                b.typeLiteral(model.templateType.asType());
                b.end(2);

                b.statement("tracer.startFunction($this)");

                b.startTryBlock();
            }

            if (isUncached) {
                b.statement("int uncachedExecuteCount = $this.uncachedExecuteCount");
            }

            b.string("loop: ").startWhile().string("true").end().startBlock();

            b.statement("int curOpcode = bc[bci]");
            b.statement("Object curObj = objs[bci]");

            if (model.enableTracing) {
                b.startIf().string("basicBlockBoundary[bci]").end().startBlock();
                b.statement("tracer.traceStartBasicBlock(bci)");
                b.end();
            }

            // b.statement("System.err.printf(\"Trace: @%04x %04x%n\", bci, curOpcode)");

            b.startTryBlock();

            b.startSwitch().string("curOpcode").end().startBlock();

            for (InstructionModel instr : model.getInstructions()) {

                if (instr.isInstrumentationOnly() && !isInstrumented) {
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
                    b.string(instr.isControlFlow() ? "1" : "0");
                    b.string((instr.signature != null && instr.signature.isVariadic) ? "1" : "0");
                    b.end(2);
                }

                switch (instr.kind) {
                    case BRANCH:
                        b.statement("int nextBci = ((IntRef) curObj).value");

                        if (isUncached) {
                            b.startIf().string("nextBci <= bci").end().startBlock();

                            b.startIf().string("uncachedExecuteCount-- <= 0").end().startBlock();
                            b.tree(createTransferToInterpreterAndInvalidate("$this"));
                            b.statement("$this.changeInterpreters(CACHED_INTERPRETER)");
                            b.statement("return (sp << 16) | nextBci");
                            b.end();

                            b.end();
                        }

                        b.statement("bci = nextBci");
                        b.statement("continue loop");
                        break;
                    case BRANCH_FALSE:
                        b.statement("Object operand = frame.getObject(sp - 1)");
                        b.statement("assert operand instanceof Boolean");
                        b.startIf().string("operand == Boolean.TRUE").end().startBlock();
                        b.statement("sp -= 1");
                        b.statement("bci += 1");
                        b.statement("continue loop");
                        b.end().startElseBlock();
                        b.statement("sp -= 1");
                        b.statement("bci = ((IntRef) curObj).value");
                        b.statement("continue loop");
                        b.end();
                        break;
                    case CUSTOM: {
                        buildCustomInstructionExecute(b, instr, true);
                        break;
                    }
                    case CUSTOM_SHORT_CIRCUIT:
                        buildCustomInstructionExecute(b, instr, false);

                        b.startIf().string("result", instr.continueWhen ? "!=" : "==", "Boolean.TRUE").end().startBlock();
                        b.startAssign("bci");
                        b.string("(");
                        if (model.generateUncached) {
                            b.string("(" + instr.getInternalName() + "Gen_UncachedData)");
                        } else {
                            b.string("(" + instr.getInternalName() + "Gen)");
                        }
                        b.string(" curObj).op_branchTarget_.value");
                        b.end();
                        b.statement("continue loop");
                        b.end().startElseBlock();
                        b.statement("sp -= 1");
                        b.statement("bci += 1");
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
                        b.statement("frame.setObject(sp, frame.getArguments()[(int) curObj])");
                        b.statement("sp += 1");
                        break;
                    case LOAD_CONSTANT:
                        b.statement("frame.setObject(sp, curObj)");
                        b.statement("sp += 1");
                        break;
                    case LOAD_LOCAL: {
                        String localFrame = model.enableYield ? "generatorFrame" : "frame";
                        if (!model.hasBoxingElimination()) {
                            b.statement("frame.setObject(sp, " + localFrame + ".getObject(((IntRef) curObj).value))");
                        } else if (isUncached) {
                            b.statement("frame.setObject(sp, " + localFrame + ".getObject(((LoadLocalData) curObj).v_index))");
                        } else {
                            b.statement("LoadLocalData curData = (LoadLocalData) curObj");
                            b.statement("int curIndex = curData.v_index");

                            b.startSwitch().string("curData.v_kind").end().startBlock();

                            // uninitialized
                            b.startCase().string("0 /* uninitialized */").end().startCaseBlock();
                            b.tree(createTransferToInterpreterAndInvalidate("$this"));
                            b.statement("doLoadLocalInitialize(frame, " + localFrame + ", sp, curData, curIndex, localBoxingState, true)");
                            b.statement("break");
                            b.end();

                            // generic
                            b.startCase().string("-1 /* boxing */").end().startCaseBlock();
                            b.startIf().string("frame.isObject(curIndex)").end().startBlock();
                            if (!model.enableYield) {
                                b.statement("frame.copyObject(curIndex, sp)");
                            } else {
                                b.statement("frame.setObject(sp, generatorFrame.getObject(curIndex))");
                            }
                            b.end().startElseBlock();
                            b.tree(createTransferToInterpreterAndInvalidate("$this"));
                            b.statement("Object value = " + localFrame + ".getValue(curIndex)");
                            b.statement(localFrame + ".setObject(curIndex, value)");
                            b.statement("frame.setObject(sp, value)");
                            b.end();
                            b.statement("break");
                            b.end();

                            for (TypeMirror mir : model.boxingEliminatedTypes) {
                                String frameName = firstLetterUpperCase(mir.toString());

                                b.startCase().tree(boxingTypeToInt(mir)).end().startCaseBlock();
                                b.startIf().string("frame.is" + frameName + "(curIndex)").end().startBlock();
                                b.statement("frame.copyPrimitive(curIndex, sp)");
                                b.end().startElseBlock();
                                b.tree(createTransferToInterpreterAndInvalidate("$this"));
                                b.statement("doLoadLocalInitialize(frame, " + localFrame + ", sp, curData, curIndex, localBoxingState, false)");
                                b.end();
                                b.statement("break");
                                b.end();

                                b.startCase().tree(boxingTypeToInt(mir)).string("| 0x40 /* (boxed) */").end().startCaseBlock();
                                b.startIf().string("frame.is" + frameName + "(curIndex)").end().startBlock();
                                b.statement("frame.setObject(sp, frame.get" + frameName + "(curIndex))");
                                b.end().startElseBlock();
                                b.tree(createTransferToInterpreterAndInvalidate("$this"));
                                b.statement("doLoadLocalInitialize(frame, " + localFrame + ", sp, curData, curIndex, localBoxingState, false)");
                                b.end();
                                b.statement("break");
                                b.end();
                            }

                            b.caseDefault().startCaseBlock();
                            b.tree(createShouldNotReachHere());
                            b.end();

                            b.end();
                        }
                        b.statement("sp += 1");
                        break;
                    }
                    case LOAD_LOCAL_MATERIALIZED:
                        b.statement("VirtualFrame matFrame = (VirtualFrame) frame.getObject(sp - 1)");
                        b.statement("frame.setObject(sp - 1, matFrame.getObject(((IntRef) curObj).value))");
                        break;
                    case POP:
                        b.statement("frame.clear(sp - 1)");
                        b.statement("sp -= 1");
                        break;
                    case RETURN:
                        if (isUncached) {
                            b.startIf().string("uncachedExecuteCount-- <= 0").end().startBlock();
                            b.tree(createTransferToInterpreterAndInvalidate("$this"));
                            b.statement("$this.changeInterpreters(CACHED_INTERPRETER)");
                            b.end().startElseBlock();
                            b.statement("$this.uncachedExecuteCount = uncachedExecuteCount");
                            b.end();
                        }

                        b.statement("return ((sp - 1) << 16) | 0xffff");
                        break;
                    case STORE_LOCAL: {
                        String localFrame = model.enableYield ? "generatorFrame" : "frame";
                        if (!model.hasBoxingElimination()) {
                            b.statement(localFrame + ".setObject(((IntRef) curObj).value, frame.getObject(sp - 1))");
                        } else if (isUncached) {
                            b.statement(localFrame + ".setObject(((StoreLocalData) curObj).s_index, frame.getObject(sp - 1))");
                        } else {
                            b.statement("StoreLocalData curData = (StoreLocalData) curObj");
                            b.statement("int curIndex = curData.s_index");

                            b.startSwitch().string("localBoxingState[curIndex]").end().startBlock();

                            b.startCase().string("0 /* uninitialized */").end().startBlock();
                            b.tree(createTransferToInterpreterAndInvalidate("$this"));
                            b.statement("doStoreLocalInitialize(frame, " + localFrame + ", sp, localBoxingState, curIndex)");
                            b.statement("break");
                            b.end();

                            b.startCase().string("-1 /* boxing */").end().startBlock();
                            b.statement(localFrame + ".setObject(curIndex, doPopObject(frame, $this, sp - 1, curData.s_childIndex, objs))");
                            b.statement("break");
                            b.end();

                            for (TypeMirror mir : model.boxingEliminatedTypes) {

                                String frameName = firstLetterUpperCase(mir.toString());

                                b.startCase().tree(boxingTypeToInt(mir)).end().startBlock();

                                b.startTryBlock();
                                b.statement(localFrame + ".set" + frameName + "(curIndex, doPopPrimitive" + frameName + "(frame, $this, sp - 1, curData.s_childIndex, objs))");
                                b.end().startCatchBlock(types.UnexpectedResultException, "ex");
                                b.statement("localBoxingState[curIndex] = -1");
                                b.statement(localFrame + ".setObject(curIndex, ex.getResult())");
                                b.end();
                                b.statement("break");
                                b.end();
                            }

                            b.end();
                        }
                        b.statement("frame.clear(sp - 1)");
                        b.statement("sp -= 1");
                        break;
                    }
                    case STORE_LOCAL_MATERIALIZED:
                        b.statement("VirtualFrame matFrame = (VirtualFrame) frame.getObject(sp - 2)");
                        b.statement("matFrame.setObject(((IntRef) curObj).value, frame.getObject(sp - 1))");
                        b.statement("frame.clear(sp - 1)");
                        b.statement("frame.clear(sp - 2)");
                        b.statement("sp -= 2");
                        break;
                    case THROW:
                        b.statement("throw sneakyThrow((Throwable) frame.getObject(((IntRef) curObj).value))");
                        break;
                    case YIELD:
                        b.statement("frame.copyTo(numLocals, generatorFrame, numLocals, (sp - 1 - numLocals))");
                        b.statement("frame.setObject(sp - 1, ((ContinuationLocation) curObj).createResult(generatorFrame, frame.getObject(sp - 1)))");
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
                        b.statement("frame.setObject(sp - 1, mergeVariadic((Object[])frame.getObject(sp - 1)))");
                        break;

                    default:
                        throw new UnsupportedOperationException("not implemented");
                }

                if (!instr.isControlFlow()) {
                    b.statement("bci += 1");
                    b.statement("continue loop");
                }

                b.end();

            }

            b.end(); // switch

            b.end().startCatchBlock(context.getDeclaredType("com.oracle.truffle.api.exception.AbstractTruffleException"), "ex");

            // b.statement("System.err.printf(\" Caught %s @ %04x ... \", ex, bci)");

            b.startFor().string("int idx = 0; idx < handlers.length; idx += 5").end().startBlock();

            // todo: this could get improved
            b.startIf().string("handlers[idx] > bci").end().startBlock().statement("continue").end();
            b.startIf().string("handlers[idx + 1] <= bci").end().startBlock().statement("continue").end();

            b.statement("bci = handlers[idx + 2]");
            b.statement("sp = handlers[idx + 3] + numLocals");
            b.statement("frame.setObject(handlers[idx + 4], ex)");

            // b.statement("System.err.printf(\"going to %04x%n\", bci)");

            b.statement("continue loop");

            b.end(); // for

            // b.statement("System.err.printf(\"rethrowing%n\")");

            b.statement("throw ex");

            b.end(); // catch

            b.end(); // while (true)

            if (model.enableTracing) {
                b.end().startFinallyBlock();
                b.statement("tracer.endFunction($this)");
                b.end();
            }

            return ex;
        }

        private void buildCustomInstructionExecute(CodeTreeBuilder b, InstructionModel instr, boolean doPush) {
            TypeMirror genType = new GeneratedTypeMirror("", instr.getInternalName() + "Gen");
            TypeMirror uncachedType = new GeneratedTypeMirror("", instr.getInternalName() + "Gen_UncachedData");
            CustomSignature signature = instr.signature;

            if (!isUncached && model.enableTracing) {
                b.startBlock();

                b.startAssign("var specInfo").startStaticCall(types.Introspection, "getSpecializations");
                b.startGroup().cast(genType).string("curObj").end();
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

            String extraArguments = "$this, objs, bci, sp";

            if (doPush) {
                int stackOffset = -instr.signature.valueCount + (instr.signature.isVoid ? 0 : 1);
                b.statement("int resultSp = sp + " + stackOffset);
            }

            if (isUncached) {

                if (instr.needsUncachedData()) {
                    b.declaration(uncachedType, "opUncachedData");
                    b.startAssign("opUncachedData").cast(uncachedType).string("curObj").end();
                }

                if (signature.isVoid) {
                    b.startStatement();
                } else {
                    b.startAssign("Object result");
                }

                b.staticReference(genType, "UNCACHED").startCall(".executeUncached");
                b.string("frame");

                for (int i = 0; i < instr.signature.valueCount; i++) {
                    TypeMirror targetType = instr.signature.valueTypes[i];
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

                for (int i = 0; i < instr.signature.localSetterCount; i++) {
                    b.string("opUncachedData.op_localSetter" + i + "_");
                }

                for (int i = 0; i < instr.signature.localSetterRangeCount; i++) {
                    b.string("opUncachedData.op_localSetterRange" + i + "_");
                }

                b.string(extraArguments);
                b.end(2);

                if (!signature.isVoid && doPush) {
                    b.statement("frame.setObject(resultSp - 1, result)");
                }
            } else if (signature.isVoid) {
                b.startStatement();
                b.startParantheses().cast(genType).string("curObj").end().startCall(".executeVoid");
                b.string("frame");
                b.string(extraArguments);
                b.end(2);
            } else if (signature.resultBoxingElimination) {

                if (!doPush) {
                    throw new AssertionError("RBE is set for " + instr.name + " but !doPush");
                }

                b.startBlock();
                b.declaration(genType, "nodeImpl");
                b.startAssign("nodeImpl").cast(genType).string("curObj").end();

                b.startSwitch().string("nodeImpl.op_resultType_").end().startBlock();

                b.startCase().string("0 /* uninitialized */").end();
                b.startCase().string("-1 /* boxing */").end().startCaseBlock();
                // object case
                b.startStatement().startCall("frame.setObject");
                b.string("resultSp - 1");
                b.startCall("nodeImpl.executeObject");
                b.string("frame").string(extraArguments);
                b.end(3);
                b.statement("break");
                b.end();

                for (TypeMirror mir : model.boxingEliminatedTypes) {
                    String boxingEliminatedType = firstLetterUpperCase(mir.toString());

                    b.startCase().tree(boxingTypeToInt(mir)).end().startBlock();

                    if (signature.possibleBoxingResults == null || signature.possibleBoxingResults.contains(mir)) {
                        // Invoke the specialization that returns the boxing-eliminated type.
                        b.startTryBlock();

                        b.startStatement().startCall("frame.set" + boxingEliminatedType);
                        b.string("resultSp - 1");
                        b.startCall("nodeImpl.execute" + boxingEliminatedType);
                        b.string("frame").string(extraArguments);
                        b.end(3);

                        b.end().startCatchBlock(types.UnexpectedResultException, "ex");

                        b.tree(createTransferToInterpreterAndInvalidate("$this"));
                        b.statement("nodeImpl.op_resultType_ = -1 /* boxing */ ");
                        b.statement("frame.setObject(resultSp - 1, ex.getResult())");

                        b.end();
                    } else {
                        // Just invoke executeObject.
                        b.statement("Object result = nodeImpl.executeObject(frame, " + extraArguments + ")");

                        b.startIf().string("result").instanceOf(boxType(mir)).end().startBlock();

                        b.statement("frame.set" + boxingEliminatedType + "(resultSp - 1, (" + mir + ") result)");

                        b.end().startElseBlock();

                        b.tree(createTransferToInterpreterAndInvalidate("$this"));
                        b.statement("nodeImpl.op_resultType_ = -1 /* boxing */");
                        b.statement("frame.setObject(resultSp - 1, result)");

                        b.end();
                    }

                    b.statement("break");
                    b.end();
                }

                b.caseDefault().startCaseBlock();
                b.tree(createShouldNotReachHere("tried to BE " + instr.name + " as type \" + nodeImpl.op_resultType_ + \" but no bueno"));
                b.end();

                b.end();

                b.end();
            } else {
                // non-boxing-eliminated, non-void, cached
                b.startAssign("Object result");
                b.startParantheses().cast(genType).string("curObj").end().startCall(".executeObject");
                b.string("frame");
                b.string(extraArguments);
                b.end(2);

                if (doPush) {
                    b.statement("frame.setObject(resultSp - 1, result)");
                }
            }

            for (int i = 0; i < instr.signature.valueCount - (instr.signature.isVoid ? 0 : 1); i++) {
                b.statement("frame.clear(resultSp + " + i + ")");
            }

            if (doPush) {
                b.statement("sp = resultSp");
            }
        }

        private CodeExecutableElement createDoLoadLocalInitialize() {
            CodeExecutableElement ex = new CodeExecutableElement(context.getType(void.class), "doLoadLocalInitialize");
            ex.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));
            ex.addParameter(new CodeVariableElement(types.VirtualFrame, "localFrame"));
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "sp"));
            ex.addParameter(new CodeVariableElement(loadLocalData.asType(), "curData"));
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "curIndex"));
            ex.addParameter(new CodeVariableElement(context.getType(byte[].class), "localBoxingState"));
            ex.addParameter(new CodeVariableElement(context.getType(boolean.class), "prim"));
            CodeTreeBuilder b = ex.createBuilder();

            b.statement("Object value = localFrame.getValue(curIndex)");
            b.statement("frame.setObject(sp, value)");

            b.statement("byte lbs = localBoxingState[curIndex]");

            b.startIf().string("prim && lbs != -1").end().startBlock();

            for (TypeMirror mir : model.boxingEliminatedTypes) {
                String frameName = firstLetterUpperCase(mir.toString());

                b.startIf().string("(lbs == 0 || lbs == ").tree(boxingTypeToInt(mir)).string(") && value").instanceOf(boxType(mir)).end().startBlock();
                b.startAssign("curData.v_kind").tree(boxingTypeToInt(mir)).string(" | 0x40 /* (boxed) */").end();
                b.statement("localFrame.set" + frameName + "(curIndex, (" + mir + ") value)");
                b.startAssign("localBoxingState[curIndex]").tree(boxingTypeToInt(mir)).end();
                b.returnStatement();
                b.end();
            }

            b.end();

            b.startAssign("curData.v_kind").string("-1").end();
            b.statement("localFrame.setObject(curIndex, value)");
            b.statement("localBoxingState[curIndex] = -1");

            return ex;
        }

        private CodeExecutableElement createDoStoreLocalInitialize() {
            CodeExecutableElement ex = new CodeExecutableElement(context.getType(void.class), "doStoreLocalInitialize");
            ex.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));
            ex.addParameter(new CodeVariableElement(types.VirtualFrame, "localFrame"));
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "sp"));
            ex.addParameter(new CodeVariableElement(context.getType(byte[].class), "localBoxingState"));
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "curIndex"));
            CodeTreeBuilder b = ex.createBuilder();

            b.tree(createNeverPartOfCompilation());

            b.startAssert().string("frame.isObject(sp - 1)").end();
            b.statement("Object value = frame.getObject(sp - 1)");

            for (TypeMirror mir : model.boxingEliminatedTypes) {
                String frameName = firstLetterUpperCase(mir.toString());

                b.startIf().string("value").instanceOf(boxType(mir)).end().startBlock();
                b.startAssign("localBoxingState[curIndex]").tree(boxingTypeToInt(mir)).end();
                b.statement("localFrame.set" + frameName + "(curIndex, (" + mir + ") value)");
                b.returnStatement();
                b.end();
            }

            b.startAssign("localBoxingState[curIndex]").string("-1").end();
            b.statement("localFrame.setObject(curIndex, value)");

            return ex;
        }
    }

    class OperationLocalImplFactory {
        private CodeTypeElement create() {
            operationLocalImpl.setSuperClass(generic(types.OperationLocal, model.templateType.asType()));
            operationLocalImpl.setEnclosingElement(operationNodeGen);

            operationLocalImpl.add(new CodeVariableElement(intRef.asType(), "index"));

            operationLocalImpl.add(createConstructorUsingFields(Set.of(), operationLocalImpl, null));

            return operationLocalImpl;
        }
    }

    class OperationLabelImplFactory {
        private CodeTypeElement create() {
            operationLabelImpl.setSuperClass(generic(types.OperationLabel, model.templateType.asType()));
            operationLabelImpl.setEnclosingElement(operationNodeGen);

            operationLabelImpl.add(new CodeVariableElement(intRef.asType(), "index"));
            operationLabelImpl.add(new CodeVariableElement(context.getType(int.class), "declaringOp"));
            operationLabelImpl.add(new CodeVariableElement(context.getType(int.class), "finallyTryOp"));

            operationLabelImpl.add(createConstructorUsingFields(Set.of(), operationLabelImpl, null));

            return operationLabelImpl;
        }
    }

    class IntRefFactory {
        private CodeTypeElement create() {
            intRef.setEnclosingElement(operationNodeGen);

            intRef.add(createConstructorUsingFields(Set.of(), intRef, null));

            intRef.add(new CodeVariableElement(context.getType(int.class), "value"));

            intRef.add(createConstructorUsingFields(Set.of(), intRef, null));

            return intRef;
        }
    }

    class LoadLocalDataFactory {
        private CodeTypeElement create() {
            loadLocalData.setEnclosingElement(operationNodeGen);
            loadLocalData.add(new CodeVariableElement(Set.of(FINAL), context.getType(short.class), "v_index"));
            loadLocalData.add(createConstructorUsingFields(Set.of(), loadLocalData, null));
            loadLocalData.add(compFinal(new CodeVariableElement(context.getType(byte.class), "v_kind")));

            loadLocalData.getImplements().add(boxableInterface.asType());
            loadLocalData.add(createSetBoxing());

            return loadLocalData;
        }

        private CodeExecutableElement createSetBoxing() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement((DeclaredType) boxableInterface.asType(), "setBoxing");
            CodeTreeBuilder b = ex.createBuilder();
            b.tree(createNeverPartOfCompilation());
            b.startAssert().string("index == 0").end();
            b.statement("v_kind = kind");
            return ex;
        }
    }

    class StoreLocalDataFactory {
        private CodeTypeElement create() {
            storeLocalData.setEnclosingElement(operationNodeGen);
            storeLocalData.add(new CodeVariableElement(Set.of(FINAL), context.getType(short.class), "s_index"));
            storeLocalData.add(createConstructorUsingFields(Set.of(), storeLocalData, null));

            storeLocalData.add(new CodeVariableElement(context.getType(int.class), "s_childIndex"));

            return storeLocalData;
        }
    }

    // todo: the next two classes could probably be merged into one
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
    }

    class ContinuationLocationImplFactory {
        private CodeTypeElement create() {

            continuationLocationImpl.setEnclosingElement(operationNodeGen);
            continuationLocationImpl.setSuperClass(types.ContinuationLocation);

            continuationLocationImpl.add(new CodeVariableElement(Set.of(FINAL), context.getType(int.class), "entry"));
            continuationLocationImpl.add(new CodeVariableElement(Set.of(FINAL), context.getType(int.class), "target"));

            continuationLocationImpl.add(createConstructorUsingFields(Set.of(), continuationLocationImpl, null));

            continuationLocationImpl.add(new CodeVariableElement(types.RootNode, "rootNode"));

            continuationLocationImpl.add(createGetRootNode());
            continuationLocationImpl.add(createToString());

            return continuationLocationImpl;
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

            b.statement("return String.format(\"ContinuationLocation [index=%d, sp=%d, bci=%04x]\", entry, (target >> 16) & 0xffff, target & 0xffff)");

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

            if (instr.needsUncachedData()) {
                CodeTypeElement uncachedType = new CodeTypeElement(Set.of(PRIVATE, STATIC), ElementKind.CLASS, null, el.getSimpleName() + "_UncachedData");
                uncachedType.setSuperClass(types.Node);
                uncachedType.setEnclosingElement(operationNodeGen);
                operationNodeGen.add(uncachedType);

                el.setSuperClass(uncachedType.asType());

                for (InstructionField field : instr.getUncachedFields()) {
                    uncachedType.add(new CodeVariableElement(field.type, field.name));
                }
            }

            int index = 0;
            for (InstructionField field : instr.getCachedFields()) {
                el.getEnclosedElements().add(index++, new CodeVariableElement(field.type, field.name));
            }

            if (instr.signature.resultBoxingElimination) {
                el.getInterfaces().add(boxableInterface.asType());
                el.add(createSetBoxing(instr));
            }

            if (OperationsNodeFactory.this.model.generateUncached) {
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

    private CodeTree createInstructionConstant(InstructionModel instr) {
        return CodeTreeBuilder.createBuilder().staticReference(instructionsElement.asType(), instr.getConstantName()).build();
    }

    private CodeTree createOperationConstant(OperationModel op) {
        return CodeTreeBuilder.createBuilder().staticReference(operationsElement.asType(), op.getConstantName()).build();
    }
}
