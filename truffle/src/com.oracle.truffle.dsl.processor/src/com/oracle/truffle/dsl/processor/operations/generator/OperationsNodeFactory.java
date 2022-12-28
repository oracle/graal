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

import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.createConstructorUsingFields;
import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.createNeverPartOfCompilation;
import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.createShouldNotReachHere;
import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.createTransferToInterpreterAndInvalidate;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.boxType;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.firstLetterUpperCase;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

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
import com.oracle.truffle.dsl.processor.generator.StaticConstants;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationValue;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeNames;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.DeclaredCodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.java.model.GeneratedTypeMirror;
import com.oracle.truffle.dsl.processor.operations.model.InstructionModel;
import com.oracle.truffle.dsl.processor.operations.model.InstructionModel.InstructionField;
import com.oracle.truffle.dsl.processor.operations.model.OperationModel;
import com.oracle.truffle.dsl.processor.operations.model.OperationModel.CustomSignature;
import com.oracle.truffle.dsl.processor.operations.model.OperationModel.OperationKind;
import com.oracle.truffle.dsl.processor.operations.model.OperationsModel;

public class OperationsNodeFactory {
    private final ProcessorContext context;
    private final TruffleTypes types;
    private final OperationsModel model;

    private CodeTypeElement operationNodeGen;
    private CodeTypeElement operationBuilder = new CodeTypeElement(Set.of(PUBLIC, STATIC, FINAL), ElementKind.CLASS, null, "Builder");
    private CodeTypeElement operationNodes = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "OperationNodesImpl");

    private CodeTypeElement intRef = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "IntRef");
    private CodeTypeElement loadLocalData = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "LoadLocalData");
    private CodeTypeElement storeLocalData = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "StoreLocalData");
    private CodeTypeElement operationLocalImpl = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "OperationLocalImpl");
    private CodeTypeElement operationLabelImpl = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "OperationLabelImpl");

    private CodeTypeElement baseInterpreter = new CodeTypeElement(Set.of(PRIVATE, STATIC, ABSTRACT), ElementKind.CLASS, null, "BaseInterpreter");
    private CodeTypeElement uncachedInterpreter;
    private CodeTypeElement cachedInterpreter = new CodeTypeElement(Set.of(PRIVATE, STATIC), ElementKind.CLASS, null, "CachedInterpreter");
    private CodeTypeElement instrumentableInterpreter = new CodeTypeElement(Set.of(PRIVATE, STATIC), ElementKind.CLASS, null, "InstrumentableInterpreter");
    private CodeTypeElement boxableInterface = new CodeTypeElement(Set.of(PRIVATE), ElementKind.INTERFACE, null, "BoxableInterface");

    private static final Name Uncached_Name = CodeNames.of("Uncached");

    public OperationsNodeFactory(ProcessorContext context, OperationsModel model) {
        this.context = context;
        this.types = context.getTypes();
        this.model = model;
    }

    public CodeTypeElement create() {
        operationNodeGen = GeneratorUtils.createClass(model.templateType, null, Set.of(PUBLIC, FINAL), model.templateType.getSimpleName() + "Gen", model.templateType.asType());
        GeneratorUtils.addSuppressWarnings(context, operationNodeGen, "all");

        if (model.generateUncached) {
            uncachedInterpreter = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "UncachedInterpreter");
        }

        CodeTreeBuilder b = operationNodeGen.createDocBuilder();
        b.startDoc();
        b.lines(model.infodump());
        b.end();

        operationNodeGen.add(new BaseInterpreterFactory().create());

        if (model.generateUncached) {
            operationNodeGen.add(new InterpreterFactory(uncachedInterpreter, true, false).create());
            operationNodeGen.add(createInterpreterSwitch(uncachedInterpreter, "UNCACHED"));
        }

        operationNodeGen.add(new InterpreterFactory(cachedInterpreter, false, false).create());
        operationNodeGen.add(new InterpreterFactory(instrumentableInterpreter, false, true).create());
        operationNodeGen.add(createInterpreterSwitch(cachedInterpreter, "CACHED"));
        operationNodeGen.add(createInterpreterSwitch(instrumentableInterpreter, "INSTRUMENTABLE"));

        operationNodeGen.add(new BuilderFactory().create());
        operationNodeGen.add(new OperationNodesImplFactory().create());
        operationNodeGen.add(new IntRefFactory().create());
        operationNodeGen.add(new OperationLocalImplFactory().create());
        operationNodeGen.add(new OperationLabelImplFactory().create());
        operationNodeGen.add(new BoxableInterfaceFactory().create());
        if (model.hasBoxingElimination()) {
            operationNodeGen.add(new LoadLocalDataFactory().create());
            operationNodeGen.add(new StoreLocalDataFactory().create());
        }

        operationNodeGen.add(createFrameDescriptorConstructor());
        operationNodeGen.add(createFrameDescriptorBuliderConstructor());

        operationNodeGen.add(createCreate());

        operationNodeGen.add(createExecute());

        operationNodeGen.add(createGetIntrospectionData());

        operationNodeGen.add(createChangeInterpreters());

        operationNodeGen.add(createGetSourceSection());
        operationNodeGen.add(createGetSourceSectionAtBci());
        operationNodeGen.add(createCloneUninitializedSupported());
        operationNodeGen.add(createCloneUninitialized());

        operationNodeGen.add(compFinal(new CodeVariableElement(Set.of(PRIVATE), operationNodes.asType(), "nodes")));
        operationNodeGen.add(compFinal(1, new CodeVariableElement(Set.of(PRIVATE), context.getType(short[].class), "bc")));
        operationNodeGen.add(compFinal(1, new CodeVariableElement(Set.of(PRIVATE), context.getType(Object[].class), "objs")));
        operationNodeGen.add(compFinal(1, new CodeVariableElement(Set.of(PRIVATE), context.getType(int[].class), "handlers")));
        operationNodeGen.add(compFinal(1, new CodeVariableElement(Set.of(PRIVATE), context.getType(int[].class), "sourceInfo")));
        operationNodeGen.add(compFinal(new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "numLocals")));
        operationNodeGen.add(compFinal(new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "buildIndex")));
        if (model.hasBoxingElimination()) {
            operationNodeGen.add(compFinal(new CodeVariableElement(Set.of(PRIVATE), context.getType(byte[].class), "localBoxingState")));
        }
        if (model.generateUncached) {
            operationNodeGen.add(new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "uncachedExecuteCount")).createInitBuilder().string("16");
        }
        operationNodeGen.add(createInterpreterField());

        operationNodeGen.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), context.getType(Object.class), "EPSILON = new Object()"));

        operationNodeGen.add(createReadVariadic());
        if (model.hasBoxingElimination()) {
            operationNodeGen.add(createDoPopObject());
            for (TypeMirror type : model.boxingEliminatedTypes) {
                operationNodeGen.add(createDoPopPrimitive(type));
            }
        }

        StaticConstants consts = new StaticConstants();
        for (InstructionModel instr : model.getInstructions()) {
            if (instr.nodeData == null) {
                continue;
            }

            OperationNodeGeneratorPlugs plugs = new OperationNodeGeneratorPlugs(context, operationNodeGen.asType(), instr);
            FlatNodeGenFactory factory = new FlatNodeGenFactory(context, GeneratorMode.DEFAULT, instr.nodeData, consts, plugs);

            CodeTypeElement el = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, instr.getInternalName() + "Gen");
            el.setSuperClass(types.Node);
            factory.create(el);
            new CustomInstructionNodeFactory().processNodeType(el, instr);
            operationNodeGen.add(el);
        }

        operationNodeGen.addAll(consts.elements());

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
            b.startCase().string(instr.id + " /* " + instr.name + " */").end().startBlock();

            switch (instr.kind) {
                case CUSTOM:
                case CUSTOM_SHORT_CIRCUIT:
                    String udName = instr.getInternalName() + "Gen" + (model.generateUncached && instr.needsUncachedData() ? "_UncachedData" : "");
                    b.declaration(udName, "curData", "(" + udName + ") objs[bci]");
                    b.declaration(udName, "newData", "new " + udName + "()");

                    for (InstructionField field : instr.getUncachedFields()) {
                        b.statement("newData." + field.name + " = curData." + field.name);
                    }

                    b.statement("clone.objs[bci] = newData");

                    break;
                default:
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

    private CodeVariableElement createInterpreterSwitch(CodeTypeElement interpreterType, String name) {
        CodeVariableElement fld = new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), interpreterType.asType(), name + "_INTERPRETER");
        fld.createInitBuilder().startNew(interpreterType.asType()).end();
        return fld;
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
        CodeTreeBuilder b = ex.createBuilder();

        b.statement("int state = numLocals << 16");

        b.startWhile().string("true").end().startBlock();
        b.startAssign("state").startCall("interpreter.continueAt");
        b.string("this, frame, bc, objs, handlers, state");
        if (model.hasBoxingElimination()) {
            b.string("localBoxingState");
        }
        b.end(2);
        b.startIf().string("(state & 0xffff) == 0xffff").end().startBlock();
        b.statement("break");
        b.end().startElseBlock();
        b.tree(createTransferToInterpreterAndInvalidate());
        b.end();
        b.end();

        b.startReturn().string("frame.getObject((state >> 16) & 0xffff)").end();

        return ex;
    }

    private CodeExecutableElement createCreate() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC, STATIC), generic(types.OperationNodes, model.templateType.asType()), "create");
        ex.addParameter(new CodeVariableElement(types.OperationConfig, "config"));
        ex.addParameter(new CodeVariableElement(generic(types.OperationParser, operationBuilder.asType()), "generator"));

        CodeTreeBuilder b = ex.getBuilder();

        b.declaration("OperationNodesImpl", "nodes", "new OperationNodesImpl(generator)");
        b.startAssign("Builder builder").startNew(operationBuilder.asType());
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

    private CodeExecutableElement createGetIntrospectionData() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), types.OperationIntrospection, "getIntrospectionData");
        CodeTreeBuilder b = ex.createBuilder();

        b.statement("Object[] instructions = new Object[bc.length]");

        b.startFor().string("int bci = 0; bci < bc.length; bci++").end().startBlock();

        b.startSwitch().string("bc[bci]").end().startBlock();

        for (InstructionModel instr : model.getInstructions()) {
            b.startCase().string("" + instr.id + " /* " + instr.name + " */").end().startBlock();
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
                case CUSTOM:
                    if (instr.signature.isVariadic) {
                        buildIntrospectionArgument(b, "VARIADIC", "((" + instr.getInternalName() + "Gen" + (model.generateUncached ? "_UncachedData" : "") + ") data).op_variadicCount_");
                    }
                    break;
                case LOAD_CONSTANT:
                    buildIntrospectionArgument(b, "CONSTANT", "data");
                    break;
                case LOAD_ARGUMENT:
                    buildIntrospectionArgument(b, "ARGUMENT", "data");
                    break;
                case LOAD_LOCAL:
                case LOAD_LOCAL_MATERIALIZED:
                case STORE_LOCAL:
                case STORE_LOCAL_MATERIALIZED:
                    buildIntrospectionArgument(b, "LOCAL", "((IntRef) data).value");
                    break;
                case CUSTOM_SHORT_CIRCUIT:
                    buildIntrospectionArgument(b, "BRANCH_OFFSET", "((" + instr.getInternalName() + "Gen" + (model.generateUncached ? "_UncachedData" : "") + " ) data).op_branchTarget_");
                    break;
            }

            b.end();

            b.end(2);
            b.statement("break");
            b.end();
        }

        b.end();

        b.end();

        b.startReturn().startStaticCall(types.OperationIntrospection_Provider, "create");
        b.string("new Object[]{0, instructions, new Object[0], null}");
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
        b.statement("result[i] = frame.getObject(sp - variadicCount + i)");
        b.end();

        b.statement("return result");

        return ex;
    }

    private CodeExecutableElement createDoPopObject() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), context.getType(Object.class), "doPopObject");
        ex.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));
        ex.addParameter(new CodeVariableElement(context.getType(int.class), "slot"));
        ex.addParameter(new CodeVariableElement(context.getType(int.class), "boxing"));
        ex.addParameter(new CodeVariableElement(context.getType(Object[].class), "objs"));

        CodeTreeBuilder b = ex.createBuilder();

        b.startIf().string("boxing == 0xffff0000 || frame.isObject(slot)").end().startBlock(); // {
        b.startReturn().string("frame.getObject(slot)").end();
        b.end(); // }

        b.tree(createTransferToInterpreterAndInvalidate());
        b.statement("((BoxableInterface) objs[boxing & 0xffff]).setBoxing((boxing >> 16) & 0xffff, (byte) -1)");
        b.startReturn().string("frame.getValue(slot)").end();

        return ex;
    }

    private CodeExecutableElement createDoPopPrimitive(TypeMirror resultType) {
        String typeName = firstLetterUpperCase(resultType.toString());
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), resultType, "doPopPrimitive" + typeName);
        ex.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));
        ex.addParameter(new CodeVariableElement(context.getType(int.class), "slot"));
        ex.addParameter(new CodeVariableElement(context.getType(int.class), "boxing"));
        ex.addParameter(new CodeVariableElement(context.getType(Object[].class), "objs"));

        ex.addThrownType(types.UnexpectedResultException);

        CodeTreeBuilder b = ex.createBuilder();

        b.startIf().string("boxing == 0xffff0000").end().startBlock(); // {
        b.statement("Object result = frame.getObject(slot)");

        b.startIf().string("result").instanceOf(boxType(resultType)).end().startBlock(); // {
        b.startReturn().cast(resultType).string("result").end();
        b.end().startElseBlock(); // } {
        b.tree(createTransferToInterpreterAndInvalidate());
        b.startThrow().startNew(types.UnexpectedResultException).string("result").end(2);
        b.end(); // }

        b.end().startElseBlock(); // } {

        b.startIf().string("frame.is" + typeName + "(slot)").end().startBlock();
        b.startReturn().string("frame.get" + typeName + "(slot)").end();
        b.end().startElseBlock();
        b.tree(createTransferToInterpreterAndInvalidate());
        b.startStatement();
        b.string("((BoxableInterface) objs[boxing & 0xffff]).setBoxing((boxing >> 16) & 0xffff, (byte) ").tree(boxingTypeToInt(resultType)).string(")");
        b.end();

        b.statement("Object result = frame.getValue(slot)");

        b.startIf().string("result").instanceOf(boxType(resultType)).end().startBlock(); // {
        b.startReturn().cast(resultType).string("result").end();
        b.end().startElseBlock(); // } {
        b.tree(createTransferToInterpreterAndInvalidate());
        b.startThrow().startNew(types.UnexpectedResultException).string("result").end(2);
        b.end();

        b.end();

        b.end();

        return ex;
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

            b.startCase().string(instr.id + " /* " + instr.name + " */").end().startBlock();

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

    class BuilderFactory {

        CodeTypeElement savedState = new CodeTypeElement(Set.of(PRIVATE, STATIC), ElementKind.CLASS, null, "SavedState");

        CodeVariableElement[] builderState = new CodeVariableElement[]{
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(short[].class), "bc"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "bci"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(Object[].class), "objs"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int[].class), "operationStack"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(Object[].class), "operationData"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int[].class), "operationChildCount"),
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
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int[].class), "sourceInfo"),
                        new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "sourceInfoIndex"),

                        // must be last
                        new CodeVariableElement(Set.of(PRIVATE), savedState.asType(), "savedState"),
        };

        class SavedStateFactory {
            private CodeTypeElement create() {
                savedState.addAll(List.of(builderState));
                savedState.add(createConstructorUsingFields(Set.of(), savedState, null));

                return savedState;
            }
        }

        private CodeTypeElement create() {
            operationBuilder.setSuperClass(types.OperationBuilder);
            operationBuilder.setEnclosingElement(operationNodeGen);

            operationBuilder.add(new SavedStateFactory().create());

            operationBuilder.add(createConstructor());

            operationBuilder.add(createOperationNames());

            operationBuilder.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), operationNodes.asType(), "nodes"));
            operationBuilder.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), context.getType(boolean.class), "isReparse"));
            operationBuilder.add(new CodeVariableElement(Set.of(PRIVATE), context.getType(boolean.class), "withSource"));
            operationBuilder.add(new CodeVariableElement(Set.of(PRIVATE), context.getType(boolean.class), "withInstrumentation"));
            operationBuilder.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), generic(context.getDeclaredType(ArrayList.class), operationNodeGen.asType()), "builtNodes"));
            operationBuilder.add(new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "buildIndex"));
            operationBuilder.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), generic(context.getDeclaredType(ArrayList.class), types.Source), "sources"));

            operationBuilder.addAll(List.of(builderState));

            operationBuilder.add(createCreateLocal());
            operationBuilder.add(createCreateLabel());

            for (OperationModel operation : model.getOperations()) {
                if (operation.isVariadic || operation.numChildren > 0) {
                    operationBuilder.add(createBegin(operation));
                    operationBuilder.add(createEnd(operation));
                } else {
                    operationBuilder.add(createEmit(operation));
                }
            }

            operationBuilder.add(createBeginHelper());
            operationBuilder.add(createEndHelper());
            operationBuilder.add(createEmitHelperBegin());
            operationBuilder.add(createBeforeChild());
            operationBuilder.add(createAfterChild());
            operationBuilder.add(createEmitInstruction());
            operationBuilder.add(createdoEmitSourceInfo());
            operationBuilder.add(createFinish());

            return operationBuilder;
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

            b.startReturn().startNew(operationLocalImpl.asType()).startNew(intRef.asType()).string("numLocals++").end(3);

            return ex;
        }

        private CodeExecutableElement createCreateLabel() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), types.OperationLabel, "createLabel");
            CodeTreeBuilder b = ex.createBuilder();

            b.startReturn().startNew(operationLabelImpl.asType()).startNew(intRef.asType()).string("-1").end(3);

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
                    throw new AssertionError("e");
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

            b.startIf().string("operationStack == null").end().startBlock(); // {
            b.startThrow().startNew(context.getType(IllegalStateException.class));
            b.string("\"Unexpected operation begin - no root operation present. Did you forget a beginRoot()?\"").end();
            b.end(2);
            b.end(); // }

            b.startIf().string("operationSp == operationStack.length").end().startBlock(); // {
            b.startAssign("operationStack").startStaticCall(context.getType(Arrays.class), "copyOf");
            b.string("operationStack");
            b.string("operationStack.length * 2");
            b.end(2);
            b.startAssign("operationChildCount").startStaticCall(context.getType(Arrays.class), "copyOf");
            b.string("operationChildCount");
            b.string("operationChildCount.length * 2");
            b.end(2);
            b.startAssign("operationData").startStaticCall(context.getType(Arrays.class), "copyOf");
            b.string("operationData");
            b.string("operationData.length * 2");
            b.end(2);
            b.end(); // }

            b.statement("operationStack[operationSp] = id");
            b.statement("operationChildCount[operationSp] = 0");
            b.statement("operationData[operationSp++] = data");

            return ex;
        }

        private CodeExecutableElement createBegin(OperationModel operation) {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), context.getType(void.class), "begin" + operation.name);

            if (operation.operationArguments != null) {
                int argIndex = 0;
                for (TypeMirror argument : operation.operationArguments) {
                    ex.addParameter(new CodeVariableElement(argument, "arg" + argIndex));
                    argIndex++;
                }
            }

            CodeTreeBuilder b = ex.createBuilder();

            if (operation.isSourceOnly()) {
                b.startIf().string("!withSource").end().startBlock();
                b.returnStatement();
                b.end();
            }

            if (operation.kind == OperationKind.ROOT) {
                b.startIf().string("bc != null").end().startBlock(); // {
                b.startAssign("savedState").startNew(savedState.asType());
                b.variables(List.of(builderState));
                b.end(2);
                b.end(); // }

                b.statement("bc = new short[32]");
                b.statement("bci = 0");
                b.statement("objs = new Object[32]");
                b.statement("operationStack = new int[8]");
                b.statement("operationData = new Object[8]");
                b.statement("operationChildCount = new int[8]");
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
            b.string("" + operation.id);
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
            }

            return ex;
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

            if (operation.isSourceOnly()) {
                b.startIf().string("!withSource").end().startBlock();
                b.returnStatement();
                b.end();
            }

            b.startStatement().startCall("endOperation");
            b.string("" + operation.id);
            b.end(2);

            if (operation.isVariadic && operation.numChildren > 0) {
                b.startIf().string("operationChildCount[operationSp] < " + operation.numChildren).end().startBlock();
                buildThrowIllegalStateException(b, "\"Operation " + operation.name + " expected at least " + operation.numChildren +
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
                    b.startAssign("this." + state.getName()).string("savedState." + state.getName()).end();
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

                    b.startIf().string("exHandlers.length <= exHandlerCount + 5").end().startBlock();
                    b.statement("exHandlers = Arrays.copyOf(exHandlers, exHandlers.length * 2)");
                    b.end();

                    b.statement("exHandlers[exHandlerCount++] = (int) data[0]");
                    b.statement("exHandlers[exHandlerCount++] = (int) data[1]");
                    b.statement("exHandlers[exHandlerCount++] = (int) data[2]");
                    b.statement("exHandlers[exHandlerCount++] = (int) data[3]");
                    b.statement("exHandlers[exHandlerCount++] = ((OperationLocalImpl) data[4]).index.value");

                    b.end();
                    break;
                case CUSTOM_SHORT_CIRCUIT:
                    b.statement("((IntRef) ((Object[]) operationData[operationSp])[0]).value = bci");
                    break;
                case SOURCE_SECTION:
                    b.statement("sourceLocationSp -= 2");

                    b.startStatement().startCall("doEmitSourceInfo");
                    b.string("sourceIndexStack[sourceIndexSp - 1]");
                    b.string("sourceLocationStack[sourceLocationSp]");
                    b.string("sourceLocationStack[sourceLocationSp + 1]");
                    b.end(2);
                    break;

                case SOURCE:
                    b.statement("sourceLocationSp -= 2");
                    b.statement("sourceIndexSp -= 1");
                    b.statement("doEmitSourceInfo(sourceIndexStack[sourceIndexSp], -1, -1)");
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
                case YIELD:
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

            b.startStatement().startCall("beforeChild").end(2);
            b.startStatement().startCall("emitOperationBegin").end(2);

            if (operation.kind == OperationKind.LABEL) {
                // todo: scope check
                b.startIf().string("((OperationLabelImpl) arg0).index.value != -1").end().startBlock();
                buildThrowIllegalStateException(b, "\"OperationLabel already emitted. Each label must be emitted exactly once.\"");
                b.end();

                b.statement("((OperationLabelImpl) arg0).index.value = bci");
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
            if (model.generateUncached) {
                if (!instruction.needsUncachedData()) {
                    b.statement("Object argument = EPSILON");
                    return;
                }

                b.statement(instruction.getInternalName() + "Gen_UncachedData argument = new " + instruction.getInternalName() + "Gen_UncachedData()");

            } else {
                b.statement(instruction.getInternalName() + "Gen argument = new " + instruction.getInternalName() + "Gen()");
            }

            if (instruction.signature.isVariadic) {
                b.statement("argument.op_variadicCount_ = operationChildCount[operationSp] - " + instruction.signature.valueCount);
            }

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
                b.string("((IntRef)((OperationLocalImpl)((Object[]) operationData[operationSp])[" + (argBase + i) + "]).index).value");
                b.end(2);
            }

            argBase += instruction.signature.localSetterCount;

        }

        private CodeExecutableElement createBeforeChild() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "beforeChild");
            CodeTreeBuilder b = ex.createBuilder();

            b.statement("Object data = operationData[operationSp - 1]");
            b.statement("int childIndex = operationChildCount[operationSp - 1]");

            b.startSwitch().string("operationStack[operationSp - 1]").end().startBlock();

            for (OperationModel op : model.getOperations()) {
                if (!op.isVariadic && op.numChildren == 0) {
                    continue;
                }

                b.startCase().string(op.id + " /* " + op.name + " */").end().startBlock();

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

        private CodeExecutableElement createAfterChild() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "afterChild");
            ex.addParameter(new CodeVariableElement(context.getType(boolean.class), "producedValue"));
            CodeTreeBuilder b = ex.createBuilder();

            b.statement("Object data = operationData[operationSp - 1]");
            b.statement("int childIndex = operationChildCount[operationSp - 1]");

            b.startSwitch().string("operationStack[operationSp - 1]").end().startBlock();

            for (OperationModel op : model.getOperations()) {
                if (!op.isVariadic && op.numChildren == 0) {
                    continue;
                }

                b.startCase().string(op.id + " /* " + op.name + " */").end().startBlock();

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
                        break;
                    case CONDITIONAL:
                    case IF_THEN_ELSE:
                        b.startIf().string("childIndex == 0").end().startBlock();
                        buildEmitInstruction(b, model.branchFalseInstruction, "((IntRef[]) data)[0]");
                        b.end().startElseIf().string("childIndex == 1").end().startBlock();
                        buildEmitInstruction(b, model.branchInstruction, "((IntRef[]) data)[1]");
                        b.statement("((IntRef[]) data)[0].value = bci");
                        b.end().startElseBlock();
                        b.statement("((IntRef[]) data)[1].value = bci");
                        b.end();
                        break;
                    case WHILE:
                        b.startIf().string("childIndex == 0").end().startBlock();
                        buildEmitInstruction(b, model.branchFalseInstruction, "((IntRef[]) data)[1]");
                        b.end().startElseBlock();
                        buildEmitInstruction(b, model.branchInstruction, "((IntRef[]) data)[0]");
                        b.statement("((IntRef[]) data)[1].value = bci");
                        b.end();
                        break;
                    case TRY_CATCH:
                        b.startIf().string("childIndex == 0").end().startBlock();
                        b.statement("Object[] dArray = (Object[]) data");
                        b.statement("dArray[1] = bci");
                        b.statement("dArray[5] = new IntRef()");
                        buildEmitInstruction(b, model.branchInstruction, "dArray[5]");
                        b.statement("dArray[2] = bci");
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

        private CodeExecutableElement createEmitInstruction() {
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
            b.string("objs.length * 2");
            b.end(2);
            b.end(); // }

            b.statement("bc[bci] = (short) instr");
            b.statement("objs[bci++] = data");

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
                        if (instr.signature.isVariadic) {
                            b.statement("stackValueBciSp -= " + argument + ".op_variadicCount_");
                        }
                        for (int i = instr.signature.valueCount - 1; i >= 0; i--) {
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
                    case SUPERINSTRUCTION:
                        // todo
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
                    break;
                case BRANCH_FALSE:
                case CUSTOM_SHORT_CIRCUIT:
                case RETURN:
                case POP:
                case STORE_LOCAL:
                    b.statement("curStack -= 1");
                    break;
                case CUSTOM:
                case CUSTOM_QUICKENED:
                    if (instr.signature.isVariadic) {
                        b.statement("curStack -= " + argument + ".op_variadicCount_");
                    }
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
                case SUPERINSTRUCTION:
                    // todo
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
            b.string(instr.id + " /* " + instr.name + " */");
            b.startGroup();
            if (argument != null) {
                b.string(argument);
            } else {
                b.string("EPSILON");
            }
            b.end();
            b.end(2);
        }

        private CodeExecutableElement createdoEmitSourceInfo() {
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

        private CodeExecutableElement createConstructor() {
            CodeExecutableElement ctor = new CodeExecutableElement(Set.of(PRIVATE), null, "Builder");
            ctor.addParameter(new CodeVariableElement(operationNodes.asType(), "nodes"));
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
            operationNodes.setSuperClass(generic(types.OperationNodes, model.templateType.asType()));
            operationNodes.setEnclosingElement(operationNodeGen);

            operationNodes.add(createConstructor());
            operationNodes.add(createReparseImpl());
            operationNodes.add(createSetNodes());
            operationNodes.add(createSetSources());
            operationNodes.add(createGetSources());

            return operationNodes;
        }

        private CodeExecutableElement createConstructor() {
            CodeExecutableElement ctor = new CodeExecutableElement(null, "OperationNodesImpl");
            ctor.addParameter(new CodeVariableElement(generic(types.OperationParser, operationBuilder.asType()), "generator"));

            ctor.createBuilder().statement("super(generator)");
            return ctor;
        }

        private CodeExecutableElement createReparseImpl() {
            CodeExecutableElement ex = GeneratorUtils.overrideImplement(types.OperationNodes, "reparseImpl");
            CodeTreeBuilder b = ex.createBuilder();

            b.statement("Builder builder = new Builder(this, true, config)");

            b.startStatement().startCall("builder.builtNodes.addAll");
            b.startGroup().string("(List) ");
            b.startStaticCall(context.getType(List.class), "of").string("nodes").end();
            b.end();
            b.end(2);

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

    class BaseInterpreterFactory {
        private CodeTypeElement create() {
            baseInterpreter.add(createContinueAt());

            return baseInterpreter;
        }

        private CodeExecutableElement createContinueAt() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(ABSTRACT), context.getType(int.class), "continueAt");

            ex.addParameter(new CodeVariableElement(operationNodeGen.asType(), "$this"));
            ex.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));
            ex.addParameter(new CodeVariableElement(context.getType(short[].class), "bc"));
            ex.addParameter(new CodeVariableElement(context.getType(Object[].class), "objs"));
            ex.addParameter(new CodeVariableElement(context.getType(int[].class), "handlers"));
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "startState"));
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
            CodeExecutableElement ex = GeneratorUtils.overrideImplement((DeclaredType) baseInterpreter.asType(), "continueAt");
            CodeTreeBuilder b = ex.createBuilder();

            b.statement("int bci = startState & 0xffff");
            b.statement("int sp = (startState >> 16) & 0xffff");

            b.string("loop: ").startWhile().string("true").end().startBlock();

            b.statement("int curOpcode = bc[bci]");
            b.statement("Object curObj = objs[bci]");

            if (isUncached) {
                b.statement("int uncachedExecuteCount = $this.uncachedExecuteCount");
            }

            b.startTryBlock();

            b.startSwitch().string("curOpcode").end().startBlock();

            for (InstructionModel instr : model.getInstructions()) {

                if (instr.isInstrumentationOnly() && !isInstrumented) {
                    continue;
                }

                b.startCase().string(instr.id + " /* " + instr.name + " */").end().startBlock();

                switch (instr.kind) {
                    case BRANCH:
                        b.statement("int nextBci = ((IntRef) curObj).value");

                        if (isUncached) {
                            b.startIf().string("nextBci <= bci").end().startBlock();

                            b.startIf().string("uncachedExecuteCount-- <= 0").end().startBlock();
                            b.tree(createTransferToInterpreterAndInvalidate());
                            b.statement("$this.changeInterpreters(CACHED_INTERPRETER)");
                            b.statement("return (sp << 16) | nextBci");
                            b.end();

                            b.end();
                        }

                        b.statement("bci = nextBci");
                        b.statement("continue loop");
                        break;
                    case BRANCH_FALSE:
                        b.startIf().string("frame.getObject(sp - 1) == Boolean.TRUE").end().startBlock();
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
                    case LOAD_LOCAL:
                        if (!model.hasBoxingElimination()) {
                            b.statement("frame.setObject(sp, frame.getObject(((IntRef) curObj).value))");
                        } else if (isUncached) {
                            b.statement("frame.setObject(sp, frame.getObject(((LoadLocalData) curObj).v_index))");
                        } else {
                            b.statement("LoadLocalData curData = (LoadLocalData) curObj");
                            b.statement("int curIndex = curData.v_index");

                            b.startSwitch().string("curData.v_kind").end().startBlock();

                            b.startCase().string("0").end().startCaseBlock();
                            // uninitialized
                            b.tree(createTransferToInterpreterAndInvalidate());
                            b.statement("doLoadLocalInitialize(frame, sp, curData, curIndex, true)");
                            b.statement("break");
                            b.end();

                            b.startCase().string("-1").end().startCaseBlock();
                            // generic
                            b.startIf().string("frame.isObject(curIndex)").end().startBlock();
                            b.statement("frame.copyObject(curIndex, sp)");
                            b.end().startElseBlock();
                            b.tree(createTransferToInterpreterAndInvalidate());
                            b.statement("frame.setObject(sp, frame.getValue(curIndex))");
                            b.end();
                            b.statement("break");
                            b.end();

                            for (TypeMirror mir : model.boxingEliminatedTypes) {
                                String frameName = firstLetterUpperCase(mir.toString());

                                b.startCase().tree(boxingTypeToInt(mir)).end().startCaseBlock();
                                b.startIf().string("frame.is" + frameName + "(curIndex)").end().startBlock();
                                b.statement("frame.copyPrimitive(curIndex, sp)");
                                b.end().startElseBlock();
                                b.tree(createTransferToInterpreterAndInvalidate());
                                b.statement("doLoadLocalInitialize(frame, sp, curData, curIndex, false)");
                                b.end();
                                b.statement("break");
                                b.end();

                                b.startCase().tree(boxingTypeToInt(mir)).string("| 0x40 /* (boxed) */").end().startCaseBlock();
                                b.startIf().string("frame.is" + frameName + "(curIndex)").end().startBlock();
                                b.statement("frame.setObject(sp, frame.get" + frameName + "(curIndex))");
                                b.end().startElseBlock();
                                b.tree(createTransferToInterpreterAndInvalidate());
                                b.statement("doLoadLocalInitialize(frame, sp, curData, curIndex, false)");
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
                            b.tree(createTransferToInterpreterAndInvalidate());
                            b.statement("$this.changeInterpreters(CACHED_INTERPRETER)");
                            b.end().startElseBlock();
                            b.statement("$this.uncachedExecuteCount = uncachedExecuteCount");
                            b.end();
                        }

                        b.statement("return ((sp - 1) << 16) | 0xffff");
                        break;
                    case STORE_LOCAL:
                        if (!model.hasBoxingElimination()) {
                            b.statement("frame.setObject(((IntRef) curObj).value, frame.getObject(sp - 1))");
                        } else if (isUncached) {
                            b.statement("frame.setObject(((StoreLocalData) curObj).s_index, frame.getObject(sp - 1))");
                        } else {
                            b.statement("StoreLocalData curData = (StoreLocalData) curObj");
                            b.statement("int curIndex = curData.s_index");

                            b.startSwitch().string("localBoxingState[curIndex]").end().startBlock();

                            b.startCase().string("0").end().startBlock();
                            b.tree(createTransferToInterpreterAndInvalidate());
                            b.statement("doStoreLocalInitialize(frame, sp, localBoxingState, curIndex)");
                            b.statement("break");
                            b.end();

                            b.startCase().string("-1").end().startBlock();
                            b.statement("frame.setObject(curIndex, doPopObject(frame, sp - 1, curData.s_childIndex, objs))");
                            b.statement("break");
                            b.end();

                            for (TypeMirror mir : model.boxingEliminatedTypes) {

                                String frameName = firstLetterUpperCase(mir.toString());

                                b.startCase().tree(boxingTypeToInt(mir)).end().startBlock();

                                b.startTryBlock();
                                b.statement("frame.set" + frameName + "(curIndex, doPopPrimitive" + frameName + "(frame, sp - 1, curData.s_childIndex, objs))");
                                b.end().startCatchBlock(types.UnexpectedResultException, "ex");
                                b.statement("localBoxingState[curIndex] = -1");
                                b.statement("frame.setObject(curIndex, ex.getResult())");
                                b.end();
                                b.statement("break");
                                b.end();
                            }

                            b.end();
                        }
                        b.statement("sp -= 1");
                        break;
                    case STORE_LOCAL_MATERIALIZED:
                        b.statement("VirtualFrame matFrame = (VirtualFrame) frame.getObject(sp - 2)");
                        b.statement("matFrame.setObject(((IntRef) curObj).value, frame.getObject(sp - 1))");
                        b.statement("sp -= 2");
                        break;
                    case THROW:
                        break;
                    case YIELD:
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

            b.startFor().string("int idx = 0; idx < handlers.length; idx += 5").end().startBlock();

            b.startIf().string("handlers[idx] > bci").end().startBlock().statement("continue").end();
            b.startIf().string("handlers[idx + 1] <= bci").end().startBlock().statement("break").end();

            b.statement("bci = handlers[idx + 2]");
            b.statement("sp = handlers[idx + 3]");
            b.statement("frame.setObject(handlers[idx + 4], ex)");
            b.statement("continue loop");

            b.end(); // for

            b.statement("throw ex");

            b.end(); // catch

            b.end(); // while (true)

            return ex;
        }

        private void buildCustomInstructionExecute(CodeTreeBuilder b, InstructionModel instr, boolean doPush) {
            TypeMirror genType = new GeneratedTypeMirror("", instr.getInternalName() + "Gen");
            TypeMirror uncachedType = new GeneratedTypeMirror("", instr.getInternalName() + "Gen_UncachedData");
            CustomSignature signature = instr.signature;

            String extraArguments = "$this, objs, bci, sp";

            if (signature.isVariadic) {
                b.startAssign("int variadicCount");
                b.startParantheses().cast(uncachedType).string("curObj").end().string(".op_variadicCount_");
                b.end();
            }

            if (doPush) {
                int stackOffset = -instr.signature.valueCount + (instr.signature.isVoid ? 0 : 1);
                b.statement("int resultSp = sp + " + stackOffset + (instr.signature.isVariadic ? " - variadicCount" : ""));
            }

            if (isUncached) {
                if (signature.isVoid) {
                    b.startStatement();
                } else {
                    b.startAssign("Object result");
                }

                b.staticReference(genType, "UNCACHED").startCall(".executeUncached");
                b.string("frame");

                for (int i = 0; i < instr.signature.valueCount; i++) {
                    b.startCall("frame.getObject").startGroup();
                    b.string("sp");
                    if (signature.isVariadic) {
                        b.string(" - variadicCount");
                    }
                    b.string(" - " + (instr.signature.valueCount - i));
                    b.end(2);
                }

                if (instr.signature.isVariadic) {
                    b.string("readVariadic(frame, sp, variadicCount)");
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
                b.declaration(genType, "nObj");
                b.startAssign("nObj").cast(genType).string("curObj").end();

                b.startSwitch().string("nObj.op_resultType_").end().startBlock();

                b.startCase().string("-1").end();
                b.startCase().string("0").end().startCaseBlock();
                // object case
                b.startStatement().startCall("frame.setObject");
                b.string("resultSp - 1");
                b.startCall("nObj.executeObject");
                b.string("frame").string(extraArguments);
                b.end(3);
                b.statement("break");
                b.end();

                Set<TypeMirror> mirs = signature.possibleBoxingResults;
                if (mirs == null) {
                    mirs = model.boxingEliminatedTypes;
                }

                for (TypeMirror mir : mirs) {
                    if (ElementUtils.isObject(mir)) {
                        continue;
                    }

                    b.startCase().tree(boxingTypeToInt(mir)).end().startCaseBlock();

                    b.startTryBlock();
                    b.startStatement().startCall("frame.set" + firstLetterUpperCase(mir.toString()));
                    b.string("resultSp - 1");
                    b.startCall("nObj.execute" + firstLetterUpperCase(mir.toString()));
                    b.string("frame").string(extraArguments);
                    b.end(3);

                    b.end().startCatchBlock(types.UnexpectedResultException, "ex");

                    b.tree(createTransferToInterpreterAndInvalidate());
                    b.statement("nObj.op_resultType_ = -1");
                    b.statement("frame.setObject(resultSp - 1, ex.getResult())");

                    b.end();

                    b.statement("break");

                    b.end();
                }

                b.caseDefault().startCaseBlock();
                b.tree(createShouldNotReachHere("tried to BE " + instr.name + " as type \" + nObj.op_resultType_ + \" but no bueno"));
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

            if (doPush) {
                b.statement("sp = resultSp");
            }
        }

        private CodeExecutableElement createDoLoadLocalInitialize() {
            CodeExecutableElement ex = new CodeExecutableElement(context.getType(void.class), "doLoadLocalInitialize");
            ex.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "sp"));
            ex.addParameter(new CodeVariableElement(loadLocalData.asType(), "curData"));
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "curIndex"));
            ex.addParameter(new CodeVariableElement(context.getType(boolean.class), "prim"));
            CodeTreeBuilder b = ex.createBuilder();

            b.statement("Object value = frame.getValue(curIndex)");
            b.statement("frame.setObject(sp, value)");

            b.startIf().string("prim").end().startBlock();

            for (TypeMirror mir : model.boxingEliminatedTypes) {
                b.startIf().string("value").instanceOf(boxType(mir)).end().startBlock();
                b.startAssign("curData.v_kind").tree(boxingTypeToInt(mir)).string(" | 0x40 /* (boxed) */").end();
                b.returnStatement();
                b.end();
            }

            b.end();

            b.startAssign("curData.v_kind").string("-1").end();

            return ex;
        }

        private CodeExecutableElement createDoStoreLocalInitialize() {
            CodeExecutableElement ex = new CodeExecutableElement(context.getType(void.class), "doStoreLocalInitialize");
            ex.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));
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
                b.statement("frame.set" + frameName + "(curIndex, (" + mir + ") value)");
                b.returnStatement();
                b.end();
            }

            b.startAssign("localBoxingState[curIndex]").string("-1").end();
            b.statement("frame.setObject(curIndex, value)");

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

            b.startIf().string("v_kind == kind || v_kind == -1").end().startBlock();
            b.returnStatement();
            b.end();

            b.startIf().string("v_kind == 0").end().startBlock();
            b.statement("v_kind = kind");
            b.end();

            b.statement("v_kind = -1");
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

    private static final Set<String> EXECUTE_NAMES = Set.of("executeBoolean", "executeLong", "executeInt", "executeByte", "executeDouble", "executeFloat");

    private class CustomInstructionNodeFactory {

        @SuppressWarnings({"unchecked", "rawtypes"})
        private void processNodeType(CodeTypeElement el, InstructionModel instr) {
            for (VariableElement fld : ElementFilter.fieldsIn(el.getEnclosedElements())) {
                if (ElementUtils.getQualifiedName(fld.asType()).equals("C")) {
                    el.remove(fld);
                }
            }

            for (ExecutableElement ctor : ElementFilter.constructorsIn(el.getEnclosedElements())) {
                el.remove(ctor);
            }

            for (ExecutableElement met : ElementFilter.methodsIn(el.getEnclosedElements())) {
                if (EXECUTE_NAMES.contains(met.getSimpleName().toString())) {
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
                el.add(creatSetBoxing(instr));
            }
        }

        private CodeExecutableElement creatSetBoxing(InstructionModel instr) {
            CodeExecutableElement setBoxing = GeneratorUtils.overrideImplement((DeclaredType) boxableInterface.asType(), "setBoxing");
            CodeTreeBuilder b = setBoxing.createBuilder();

            b.tree(createNeverPartOfCompilation());

            b.startAssert().string("index == 0").end();

            b.startIf().string("this.op_resultType_ == kind").end().startBlock();
            b.returnStatement();
            b.end();

            b.startIf().string("this.op_resultType_ == 0 && (");
            Set<TypeMirror> mirs = instr.signature.possibleBoxingResults;
            if (mirs == null) {
                mirs = model.boxingEliminatedTypes;
            }
            boolean first = true;
            for (TypeMirror mir : mirs) {
                if (!ElementUtils.isPrimitive(mir)) {
                    continue;
                }
                if (first) {
                    first = false;
                } else {
                    b.string(" || ");
                }
                b.string("kind == ").tree(boxingTypeToInt(mir));
            }
            b.string(")").end().startBlock();
            b.statement("this.op_resultType_ = kind");
            b.returnStatement();
            b.end();

            b.statement("this.op_resultType_ = -1");
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

    private static TypeMirror generic(DeclaredType el, TypeMirror... args) {
        return new DeclaredCodeTypeMirror((TypeElement) el.asElement(), List.of(args));
    }

    private static ArrayType arrayOf(TypeMirror component) {
        return new CodeTypeMirror.ArrayCodeTypeMirror(component);
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
}
