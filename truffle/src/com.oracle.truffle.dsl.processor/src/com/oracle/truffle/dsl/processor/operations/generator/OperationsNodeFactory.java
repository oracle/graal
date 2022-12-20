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
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.GeneratorMode;
import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.generator.StaticConstants;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationValue;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.DeclaredCodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.java.model.GeneratedTypeMirror;
import com.oracle.truffle.dsl.processor.operations.model.InstructionModel;
import com.oracle.truffle.dsl.processor.operations.model.OperationModel;
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
    private CodeTypeElement operationLocalImpl = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "OperationLocalImpl");
    private CodeTypeElement operationLabelImpl = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "OperationLabelImpl");

    public OperationsNodeFactory(ProcessorContext context, OperationsModel model) {
        this.context = context;
        this.types = context.getTypes();
        this.model = model;
    }

    public CodeTypeElement create() {
        operationNodeGen = GeneratorUtils.createClass(model.templateType, null, Set.of(PUBLIC, FINAL), model.templateType.getSimpleName() + "Gen", model.templateType.asType());
        GeneratorUtils.addSuppressWarnings(context, operationNodeGen, "unused");

        // CodeTreeBuilder b = operationsNodeGen.createDocBuilder();
        // b.startDoc();
        // b.lines(model.infodump());
        // b.end();

        operationNodeGen.add(new BuilderFactory().create());
        operationNodeGen.add(new OperationNodesImplFactory().create());
        operationNodeGen.add(new IntRefFactory().create());
        operationNodeGen.add(new OperationLocalImplFactory().create());
        operationNodeGen.add(new OperationLabelImplFactory().create());

        operationNodeGen.add(createFrameDescriptorConstructor());
        operationNodeGen.add(createFrameDescriptorBuliderConstructor());

        operationNodeGen.add(createCreate());

        operationNodeGen.add(createExecute());

        operationNodeGen.add(createGetIntrospectionData());

        operationNodeGen.add(compFinal(1, new CodeVariableElement(Set.of(PRIVATE), context.getType(short[].class), "bc")));
        operationNodeGen.add(compFinal(1, new CodeVariableElement(Set.of(PRIVATE), context.getType(Object[].class), "objs")));
        operationNodeGen.add(compFinal(new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "numLocals")));
        operationNodeGen.add(compFinal(new CodeVariableElement(Set.of(PRIVATE), context.getType(int.class), "buildIndex")));

        operationNodeGen.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), context.getType(Object.class), "EPSILON = new Object()"));

        StaticConstants consts = new StaticConstants();
        for (InstructionModel instr : model.getInstructions()) {
            if (instr.nodeData == null) {
                continue;
            }

            FlatNodeGenFactory factory = new FlatNodeGenFactory(context, GeneratorMode.DEFAULT, instr.nodeData, consts);

            CodeTypeElement el = new CodeTypeElement(Set.of(), ElementKind.CLASS, null, instr.nodeType.getSimpleName() + "Gen");
            el.setSuperClass(types.Node);

            operationNodeGen.add(factory.create(el));
        }

        return operationNodeGen;
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

        b.returnNull();

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
            b.startAssign("instructions[bci]").startNewArray(arrayOf(context.getType(Object.class)), null);
            b.string("bci");
            b.doubleQuote(instr.name);
            b.string("new short[] {" + instr.id + "}");
            b.string("new Object[0]");
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

                        // must be last
                        new CodeVariableElement(Set.of(PRIVATE), savedState.asType(), "savedState"),
        };

        class SavedStateFactory {
            private CodeTypeElement create() {
                savedState.addAll(List.of(builderState));
                savedState.add(GeneratorUtils.createConstructorUsingFields(Set.of(), savedState, null));

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
            operationBuilder.add(createFinish());

            return operationBuilder;
        }

        private CodeExecutableElement createFinish() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), context.getType(void.class), "finish");
            CodeTreeBuilder b = ex.createBuilder();

            b.startIf().string("!isReparse").end().startBlock();
            b.startStatement().string("nodes.setNodes(builtNodes.toArray(new ").type(operationNodeGen.asType()).string("[0]))").end();
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
            } else {
                b.startStatement().startCall("beforeChild").end(2);
            }

            b.startStatement().startCall("beginOperation");
            b.string("" + operation.id);
            buildOperationBeginData(b, operation);
            b.end(2);

            return ex;
        }

        private void buildOperationBeginData(CodeTreeBuilder b, OperationModel operation) {
            switch (operation.kind) {
                case ROOT:
                    b.string("new Object[]{false, arg0}");
                    break;
                case BLOCK:
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

                b.startAssign("result.bc").string("Arrays.copyOf(bc, bci)").end();
                b.startAssign("result.objs").string("Arrays.copyOf(objs, bci)").end();
                b.startAssign("result.numLocals").string("numLocals").end();
                b.startAssign("result.buildIndex").string("buildIndex").end();

                b.startAssert().string("builtNodes.size() == buildIndex").end();
                b.statement("builtNodes.add(result)");

                b.end(); // }

                b.statement("buildIndex++");

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

            if (operation.instruction != null) {
                buildEmitInstruction(b, operation.instruction, () -> {
                    switch (operation.kind) {
                        case STORE_LOCAL:
                        case STORE_LOCAL_MATERIALIZED:
                        case LOAD_LOCAL_MATERIALIZED:
                            b.string("((OperationLocalImpl) operationData[operationSp]).index");
                            break;
                        case CUSTOM_SIMPLE:
                        case CUSTOM_SHORT_CIRCUIT:
                        case RETURN:
                        case YIELD:
                            b.string("EPSILON");
                            break;
                        default:
                            b.string("/* TODO: NOT IMPLEMENTED */");
                            break;
                    }
                });
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
                buildEmitInstruction(b, operation.instruction, () -> {
                    switch (operation.kind) {
                        case LOAD_ARGUMENT:
                        case LOAD_CONSTANT:
                            b.string("arg0");
                            break;
                        case LOAD_LOCAL:
                            b.string("((OperationLocalImpl) arg0).index");
                            break;
                        case BRANCH:
                            b.string("((OperationLabelImpl) arg0).index");
                            break;
                        case CUSTOM_SIMPLE:
                        case CUSTOM_SHORT_CIRCUIT:
                            b.string("EPSILON");
                            break;
                        default:
                            b.string("/* TODO: NOT IMPLEMENTED */");
                            break;
                    }
                });
            }

            b.startStatement().startCall("afterChild");
            b.string("" + !operation.isVoid);
            b.end(2);

            return ex;
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
                        buildEmitInstruction(b, model.branchFalseInstruction, () -> b.string("data"));
                        b.end().startElseBlock();
                        b.statement("((IntRef) data).value = bci");
                        b.end();
                        break;
                    case CONDITIONAL:
                    case IF_THEN_ELSE:
                        b.startIf().string("childIndex == 0").end().startBlock();
                        buildEmitInstruction(b, model.branchFalseInstruction, () -> b.string("((IntRef[]) data)[0]"));
                        b.end().startElseIf().string("childIndex == 1").end().startBlock();
                        buildEmitInstruction(b, model.branchInstruction, () -> b.string("((IntRef[]) data)[1]"));
                        b.statement("((IntRef[]) data)[0].value = bci");
                        b.end().startElseBlock();
                        b.statement("((IntRef[]) data)[1].value = bci");
                        b.end();
                        break;
                    case WHILE:
                        b.startIf().string("childIndex == 0").end().startBlock();
                        buildEmitInstruction(b, model.branchFalseInstruction, () -> b.string("((IntRef[]) data)[1]"));
                        b.end().startElseBlock();
                        buildEmitInstruction(b, model.branchInstruction, () -> b.string("((IntRef[]) data)[0]"));
                        b.statement("((IntRef[]) data)[1].value = bci");
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

        private void buildEmitInstruction(CodeTreeBuilder b, InstructionModel instr, Runnable argumentBuilder) {
            b.startStatement().startCall("doEmitInstruction");
            b.string(instr.id + " /* " + instr.name + " */");
            b.startGroup();
            if (argumentBuilder != null) {
                argumentBuilder.run();
            } else {
                b.string("EPSILON");
            }
            b.end();
            b.end(2);
        }

        private CodeExecutableElement createConstructor() {
            CodeExecutableElement ctor = new CodeExecutableElement(null, "Builder");
            ctor.addParameter(new CodeVariableElement(operationNodes.asType(), "nodes"));
            ctor.addParameter(new CodeVariableElement(context.getType(boolean.class), "isReparse"));
            ctor.addParameter(new CodeVariableElement(types.OperationConfig, "config"));

            CodeTreeBuilder b = ctor.createBuilder();

            b.statement("this.nodes = nodes");
            b.statement("this.isReparse = isReparse");
            b.statement("this.withSource = config.isWithSource()");
            b.statement("this.withInstrumentation = config.isWithInstrumentation()");

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
    }

    class OperationLocalImplFactory {
        private CodeTypeElement create() {
            operationLocalImpl.setSuperClass(generic(types.OperationLocal, model.templateType.asType()));
            operationLocalImpl.setEnclosingElement(operationNodeGen);

            operationLocalImpl.add(new CodeVariableElement(intRef.asType(), "index"));

            operationLocalImpl.add(GeneratorUtils.createConstructorUsingFields(Set.of(), operationLocalImpl, null));

            return operationLocalImpl;
        }
    }

    class OperationLabelImplFactory {
        private CodeTypeElement create() {
            operationLabelImpl.setSuperClass(generic(types.OperationLabel, model.templateType.asType()));
            operationLabelImpl.setEnclosingElement(operationNodeGen);

            operationLabelImpl.add(new CodeVariableElement(intRef.asType(), "index"));

            operationLabelImpl.add(GeneratorUtils.createConstructorUsingFields(Set.of(), operationLabelImpl, null));

            return operationLabelImpl;
        }
    }

    class IntRefFactory {
        private CodeTypeElement create() {
            intRef.setEnclosingElement(operationNodeGen);

            intRef.add(GeneratorUtils.createConstructorUsingFields(Set.of(), intRef, null));

            intRef.add(new CodeVariableElement(context.getType(int.class), "value"));

            intRef.add(GeneratorUtils.createConstructorUsingFields(Set.of(), intRef, null));

            return intRef;
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

    private CodeVariableElement compFinal(int dims, CodeVariableElement fld) {
        CodeAnnotationMirror mir = new CodeAnnotationMirror(types.CompilerDirectives_CompilationFinal);
        if (dims != -1) {
            mir.setElementValue("dimensions", new CodeAnnotationValue(dims));
        }
        fld.addAnnotationMirror(mir);
        return fld;
    }
}
