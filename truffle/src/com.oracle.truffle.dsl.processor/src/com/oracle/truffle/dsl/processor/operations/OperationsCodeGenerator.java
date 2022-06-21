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
package com.oracle.truffle.dsl.processor.operations;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.AnnotationProcessor;
import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.generator.CodeTypeElementFactory;
import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.ArrayCodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.DeclaredCodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.operations.Operation.BuilderVariables;
import com.oracle.truffle.dsl.processor.operations.Operation.InstrumentTag;
import com.oracle.truffle.dsl.processor.operations.instructions.CustomInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.FrameKind;
import com.oracle.truffle.dsl.processor.operations.instructions.Instruction;

public class OperationsCodeGenerator extends CodeTypeElementFactory<OperationsData> {

    private ProcessorContext context;
    private OperationsData m;

    private static final Set<Modifier> MOD_PUBLIC = Set.of(Modifier.PUBLIC);
    private static final Set<Modifier> MOD_PUBLIC_ABSTRACT = Set.of(Modifier.PUBLIC, Modifier.ABSTRACT);
    private static final Set<Modifier> MOD_PUBLIC_STATIC = Set.of(Modifier.PUBLIC, Modifier.STATIC);
    private static final Set<Modifier> MOD_PRIVATE = Set.of(Modifier.PRIVATE);
    private static final Set<Modifier> MOD_PRIVATE_STATIC = Set.of(Modifier.PRIVATE, Modifier.STATIC);
    private static final Set<Modifier> MOD_PRIVATE_STATIC_FINAL = Set.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);
    private static final Set<Modifier> MOD_PROTECTED = Set.of(Modifier.PROTECTED);
    private static final Set<Modifier> MOD_STATIC = Set.of(Modifier.STATIC);

    private OperationsBytecodeCodeGenerator bytecodeGenerator;

    private static final boolean FLAG_NODE_AST_PRINTING = false;
    private static final boolean ENABLE_INSTRUMENTATION = false;

    private static final String OPERATION_NODES_IMPL_NAME = "OperationNodesImpl";
    private static final String OPERATION_BUILDER_IMPL_NAME = "BuilderImpl";

    CodeTypeElement createOperationNodes() {
        CodeTypeElement typOperationNodes = GeneratorUtils.createClass(m, null, MOD_PRIVATE_STATIC_FINAL, OPERATION_NODES_IMPL_NAME, types.OperationNodes);
        typOperationNodes.add(GeneratorUtils.createConstructorUsingFields(Set.of(), typOperationNodes));

        CodeExecutableElement metReparse = GeneratorUtils.overrideImplement(types.OperationNodes, "reparseImpl");
        typOperationNodes.add(metReparse);

        CodeTreeBuilder b = metReparse.createBuilder();

        b.statement("BuilderImpl builder = new BuilderImpl(this, true, config)");
        b.statement("((Consumer) parse).accept(builder)");
        b.statement("builder.finish()");

        return typOperationNodes;
    }

    /**
     * Creates the builder class itself. This class only contains abstract methods, the builder
     * implementation class, and the <code>createBuilder</code> method.
     *
     * @return The created builder class
     */
    CodeTypeElement createBuilder(String simpleName) {
        CodeTypeElement typBuilder = GeneratorUtils.createClass(m, null, MOD_PUBLIC_ABSTRACT, simpleName, types.OperationBuilder);
        GeneratorUtils.addSuppressWarnings(context, typBuilder, "cast", "hiding", "unchecked", "rawtypes", "static-method");

        CodeExecutableElement ctor = GeneratorUtils.createConstructorUsingFields(MOD_PROTECTED, typBuilder);
        typBuilder.add(ctor);

        typBuilder.add(createOperationNodes());

        // begin/end or emit methods
        for (Operation op : m.getOperations()) {

            List<TypeMirror> args = op.getBuilderArgumentTypes();
            ArrayList<CodeVariableElement> params = new ArrayList<>();

            for (int i = 0; i < args.size(); i++) {
                params.add(new CodeVariableElement(args.get(i), "arg" + i));
            }

            CodeVariableElement[] paramsArr = params.toArray(new CodeVariableElement[0]);

            if (op.children != 0) {
                CodeExecutableElement metBegin = new CodeExecutableElement(MOD_PUBLIC_ABSTRACT, context.getType(void.class), "begin" + op.name, paramsArr);
                typBuilder.add(metBegin);

                CodeExecutableElement metEnd = new CodeExecutableElement(MOD_PUBLIC_ABSTRACT, context.getType(void.class), "end" + op.name);
                typBuilder.add(metEnd);
            } else {
                CodeExecutableElement metEmit = new CodeExecutableElement(MOD_PUBLIC_ABSTRACT, context.getType(void.class), "emit" + op.name, paramsArr);
                typBuilder.add(metEmit);
            }
        }

        CodeTypeElement typBuilderImpl = createBuilderImpl(typBuilder);
        typBuilder.add(typBuilderImpl);

        for (OperationMetadataData metadata : m.getMetadatas()) {
            typBuilder.add(createSetMetadata(metadata, true));
        }

        typBuilder.add(createCreateMethod(typBuilder, typBuilderImpl));

        return typBuilder;
    }

    private CodeExecutableElement createCreateMethod(CodeTypeElement typBuilder, CodeTypeElement typBuilderImpl) {
        CodeVariableElement parConfig = new CodeVariableElement(types.OperationConfig, "config");
        CodeVariableElement parParser = new CodeVariableElement(consumer(typBuilder.asType()), "generator");
        CodeExecutableElement metCreate = new CodeExecutableElement(MOD_PUBLIC_STATIC, types.OperationNodes, "create");
        metCreate.addParameter(parConfig);
        metCreate.addParameter(parParser);

        CodeTreeBuilder b = metCreate.getBuilder();

        b.declaration(types.OperationNodes, "nodes", "new OperationNodesImpl(generator)");
        b.startAssign("BuilderImpl builder").startNew(typBuilderImpl.asType());
        // (
        b.string("nodes");
        b.string("false"); // isReparse
        b.variable(parConfig);
        // )
        b.end(2);

        b.startStatement().startCall("generator", "accept");
        b.string("builder");
        b.end(2);

        b.startStatement().startCall("builder", "finish").end(2);

        b.startReturn().string("nodes").end();

        return metCreate;
    }

    CodeTypeElement createOperationNodeImpl() {
        CodeTypeElement typOperationNodeImpl = GeneratorUtils.createClass(m, null, MOD_PRIVATE_STATIC_FINAL, "OperationNodeImpl", types.OperationNode);
        typOperationNodeImpl.add(GeneratorUtils.createConstructorUsingFields(MOD_PRIVATE, typOperationNodeImpl));

        if (!m.getMetadatas().isEmpty()) {
            CodeExecutableElement staticInit = new CodeExecutableElement(MOD_STATIC, null, "<cinit>");
            typOperationNodeImpl.add(staticInit);

            CodeTreeBuilder initBuilder = staticInit.createBuilder();

            for (OperationMetadataData metadata : m.getMetadatas()) {
                String fieldName = metadata.getName();
                CodeVariableElement fldMetadata = new CodeVariableElement(MOD_PRIVATE, metadata.getType(), fieldName);

                typOperationNodeImpl.add(fldMetadata);

                initBuilder.startStatement().startCall("setMetadataAccessor");
                initBuilder.staticReference((VariableElement) metadata.getMessageElement());
                initBuilder.startGroup();
                // (
                initBuilder.string("n -> ");
                initBuilder.startParantheses().cast(typOperationNodeImpl.asType()).string("n").end().string("." + fieldName);
                // )
                initBuilder.end();
                initBuilder.end(2);
            }
        }

        return typOperationNodeImpl;
    }

    private CodeTypeElement createBuilderImpl(CodeTypeElement typBuilder) {
        CodeTypeElement typBuilderImpl = GeneratorUtils.createClass(m, null, Set.of(Modifier.PRIVATE, Modifier.STATIC), OPERATION_BUILDER_IMPL_NAME, typBuilder.asType());
        typBuilderImpl.setEnclosingElement(typBuilder);

        if (m.isTracing()) {
            String decisionsFilePath = m.getDecisionsFilePath();
            CodeExecutableElement mStaticInit = new CodeExecutableElement(MOD_STATIC, null, "<cinit>");
            typBuilderImpl.add(mStaticInit);

            CodeTreeBuilder b = mStaticInit.appendBuilder();

            b.startStatement().startStaticCall(types.ExecutionTracer, "initialize");

            b.typeLiteral(m.getTemplateType().asType());

            // destination path
            b.doubleQuote(decisionsFilePath);

            // instruction names
            b.startNewArray(new ArrayCodeTypeMirror(context.getType(String.class)), null);
            b.string("null");
            for (Instruction instr : m.getInstructions()) {
                b.doubleQuote(instr.name);
            }
            b.end();

            // specialization names

            b.startNewArray(new ArrayCodeTypeMirror(new ArrayCodeTypeMirror(context.getType(String.class))), null);
            b.string("null");
            for (Instruction instr : m.getInstructions()) {
                if (!(instr instanceof CustomInstruction)) {
                    b.string("null");
                    continue;
                }

                b.startNewArray(new ArrayCodeTypeMirror(context.getType(String.class)), null);
                CustomInstruction cinstr = (CustomInstruction) instr;
                for (String name : cinstr.getSpecializationNames()) {
                    b.doubleQuote(name);
                }
                b.end();
            }
            b.end();

            b.end(2);

        }

        CodeExecutableElement ctor = GeneratorUtils.createConstructorUsingFields(Set.of(), typBuilderImpl);
        typBuilderImpl.add(ctor);

        // operation IDs
        for (Operation op : m.getOperationsContext().operations) {
            CodeVariableElement fldId = new CodeVariableElement(MOD_PRIVATE_STATIC_FINAL, context.getType(int.class), "OP_" + OperationGeneratorUtils.toScreamCase(op.name));
            CodeTreeBuilder b = fldId.createInitBuilder();
            b.string("" + op.id);
            op.setIdConstantField(fldId);
            typBuilderImpl.add(fldId);
        }

        CodeTypeElement typOperationNodeImpl = createOperationNodeImpl();
        typBuilderImpl.add(typOperationNodeImpl);

        CodeTypeElement builderBytecodeNodeType;
        CodeTypeElement builderInstrBytecodeNodeType;
        bytecodeGenerator = new OperationsBytecodeCodeGenerator(typBuilderImpl, m, false);
        builderBytecodeNodeType = bytecodeGenerator.createBuilderBytecodeNode();
        typBuilderImpl.add(builderBytecodeNodeType);

        if (ENABLE_INSTRUMENTATION) {
            OperationsBytecodeCodeGenerator bcg = new OperationsBytecodeCodeGenerator(typBuilderImpl, m, true);
            builderInstrBytecodeNodeType = bcg.createBuilderBytecodeNode();
            typBuilderImpl.add(builderInstrBytecodeNodeType);
        } else {
            builderInstrBytecodeNodeType = null;
        }

        CodeVariableElement fldOperationData = new CodeVariableElement(types.BuilderOperationData, "operationData");

        CodeVariableElement fldBc = new CodeVariableElement(arrayOf(context.getType(byte.class)), "bc");

        CodeVariableElement fldIndent = null;
        if (FLAG_NODE_AST_PRINTING) {
            fldIndent = new CodeVariableElement(MOD_PRIVATE, context.getType(int.class), "indent");
            typBuilderImpl.add(fldIndent);
        }

        CodeVariableElement fldBci = new CodeVariableElement(context.getType(int.class), "bci");

        CodeVariableElement fldLastPush = new CodeVariableElement(context.getType(int.class), "lastChildPush");
        typBuilderImpl.add(fldLastPush);

        CodeVariableElement fldConstPool = new CodeVariableElement(types.OperationsConstantPool, "constPool");

        BuilderVariables vars = new BuilderVariables();
        vars.bc = fldBc;
        vars.bci = fldBci;
        vars.operationData = fldOperationData;
        vars.lastChildPushCount = fldLastPush;
        vars.consts = fldConstPool;

        typBuilderImpl.add(createForwardingConstructorCall(typOperationNodeImpl, "createNode"));
        typBuilderImpl.add(createForwardingConstructorCall(builderBytecodeNodeType, "createBytecode"));
        typBuilderImpl.add(createForwardingConstructorCall(null, "createInstrumentedBytecode"));

        typBuilderImpl.add(createDoLeave(vars));
        typBuilderImpl.add(createBeforeChild(vars));
        typBuilderImpl.add(createAfterChild(vars));

        // instruction IDs
        for (Instruction instr : m.getOperationsContext().instructions) {
            typBuilderImpl.addAll(instr.createInstructionFields());
        }

        typBuilderImpl.add(createBoxingDescriptors());

        builderBytecodeNodeType.add(createSetResultUnboxed());

        for (Operation op : m.getOperations()) {
            List<TypeMirror> args = op.getBuilderArgumentTypes();
            CodeVariableElement[] params = new CodeVariableElement[args.size()];

            for (int i = 0; i < params.length; i++) {
                params[i] = new CodeVariableElement(args.get(i), "arg" + i);
            }

            if (op.name.equals("Block")) {
                typBuilderImpl.add(createGetBlockOperationIndex(op));
            }

            if (op.children != 0) {
                typBuilderImpl.add(createBeginOperation(typBuilder, vars, op));
                typBuilderImpl.add(createEndOperation(typBuilder, vars, op));
            } else {
                typBuilderImpl.add(createEmitOperation(typBuilder, vars, op));
            }
        }

        for (OperationMetadataData metadata : m.getMetadatas()) {
            CodeVariableElement fldMetadata = new CodeVariableElement(MOD_PRIVATE, metadata.getType(), "metadata_" + metadata.getName());
            typBuilderImpl.add(fldMetadata);
            typBuilderImpl.add(createSetMetadata(metadata, false));
        }

        typBuilderImpl.add(createMetadataReset());
        typBuilderImpl.add(createMetadataSet(typOperationNodeImpl));

        return typBuilderImpl;

    }

    private CodeExecutableElement createMetadataReset() {
        CodeExecutableElement method = GeneratorUtils.overrideImplement(types.OperationBuilder, "resetMetadata");
        CodeTreeBuilder b = method.createBuilder();

        for (OperationMetadataData metadata : m.getMetadatas()) {
            b.startAssign("metadata_" + metadata.getName()).string("null").end();
        }

        return method;
    }

    private CodeExecutableElement createMetadataSet(CodeTypeElement typOperationNodeImpl) {
        CodeExecutableElement method = GeneratorUtils.overrideImplement(types.OperationBuilder, "assignMetadata");
        CodeTreeBuilder b = method.createBuilder();

        b.startAssign(typOperationNodeImpl.getSimpleName() + " nodeImpl").cast(typOperationNodeImpl.asType()).string("node").end();

        for (OperationMetadataData metadata : m.getMetadatas()) {
            b.statement("nodeImpl." + metadata.getName() + " = metadata_" + metadata.getName());
        }

        return method;
    }

    private CodeExecutableElement createSetMetadata(OperationMetadataData metadata, boolean isAbstract) {
        CodeVariableElement parValue = new CodeVariableElement(metadata.getType(), "value");
        CodeExecutableElement method = new CodeExecutableElement(
                        isAbstract ? MOD_PUBLIC_ABSTRACT : MOD_PUBLIC,
                        context.getType(void.class), "set" + metadata.getName(),
                        parValue);

        if (isAbstract) {
            return method;
        }

        CodeTreeBuilder b = method.createBuilder();

        b.startAssign("metadata_" + metadata.getName()).variable(parValue).end();

        return method;
    }

    private CodeExecutableElement createGetBlockOperationIndex(Operation op) {
        CodeExecutableElement result = GeneratorUtils.overrideImplement(types.OperationBuilder, "getBlockOperationIndex");

        result.getBuilder().startReturn().variable(op.idConstantField).end();

        return result;
    }

    private CodeExecutableElement createForwardingConstructorCall(TypeElement typeName, String methodName) {
        CodeExecutableElement result = GeneratorUtils.overrideImplement(types.OperationBuilder, methodName);
        CodeTreeBuilder b = result.getBuilder();

        if (typeName != null) {
            b.startReturn().startNew(typeName.asType());
            for (VariableElement par : result.getParameters()) {
                b.variable(par);
            }
            b.end(2);
        } else {
            b.startThrow().startNew(context.getType(UnsupportedOperationException.class)).doubleQuote("not implemented").end(2);
        }

        return result;
    }

    private CodeExecutableElement createEmitOperation(CodeTypeElement typBuilder, BuilderVariables vars, Operation op) {
        CodeExecutableElement metEmit = GeneratorUtils.overrideImplement(typBuilder, "emit" + op.name);
        CodeTreeBuilder b = metEmit.getBuilder();

        if (FLAG_NODE_AST_PRINTING) {
            b.statement("System.out.print(\"\\n\" + \" \".repeat(indent) + \"(" + op.name + ")\")");
        }

        if (op.isRealOperation()) {
            b.statement("doBeforeChild()");
        }

        boolean needsData = op.needsOperationData();

        if (needsData) {
            b.startAssign(vars.operationData);
            b.startNew(types.BuilderOperationData);

            b.variable(vars.operationData);
            b.variable(op.idConstantField);
            b.string("getCurStack()");
            b.string("" + op.getNumAuxValues());
            b.string("false");

            b.string("" + op.numLocalReferences());

            for (VariableElement v : metEmit.getParameters()) {
                b.variable(v);
            }
            b.end(2);
        }

        b.tree(op.createBeginCode(vars));
        b.tree(op.createEndCode(vars));

        b.startAssign(vars.lastChildPushCount).tree(op.createPushCountCode(vars)).end();

        if (needsData) {
            b.startAssign(vars.operationData).variable(vars.operationData).string(".parent").end();
        }

        if (op.isRealOperation()) {
            b.statement("doAfterChild()");
        }

        return metEmit;
    }

    private CodeExecutableElement createEndOperation(CodeTypeElement typBuilder, BuilderVariables vars, Operation op) {
        CodeExecutableElement metEnd = GeneratorUtils.overrideImplement(typBuilder, "end" + op.name);
        GeneratorUtils.addSuppressWarnings(context, metEnd, "unused");

        // if (operationData.id != ID) throw;
        // << end >>

        // operationData = operationData.parent;

        // doAfterChild();
        CodeTreeBuilder b = metEnd.getBuilder();

        if (op instanceof InstrumentTag) {
            b.startIf().string("!withInstrumentation").end().startBlock().returnStatement().end();
        }

        if (FLAG_NODE_AST_PRINTING) {
            b.statement("System.out.print(\")\")");
            b.statement("indent--");
        }

        b.startIf().string("operationData.operationId != ").variable(op.idConstantField).end();
        b.startBlock();
        b.startThrow().startNew(context.getType(IllegalStateException.class)).startGroup().doubleQuote("Mismatched begin/end, expected ").string(" + operationData.operationId").end(3);
        b.end();

        vars.numChildren = new CodeVariableElement(context.getType(int.class), "numChildren");
        b.declaration("int", "numChildren", "operationData.numChildren");

        if (!op.isVariableChildren()) {
            b.startIf().string("numChildren != " + op.children).end();
            b.startBlock();
            b.startThrow().startNew(context.getType(IllegalStateException.class)).startGroup().doubleQuote(op.name + " expected " + op.children + " children, got ").string(" + numChildren").end(3);
            b.end();
        } else {
            b.startIf().string("numChildren < " + op.minimumChildren()).end();
            b.startBlock();
            b.startThrow().startNew(context.getType(IllegalStateException.class)).startGroup().doubleQuote(op.name + " expected at least " + op.minimumChildren() + " children, got ").string(
                            " + numChildren").end(3);
            b.end();
        }

        b.tree(op.createEndCode(vars));

        CodeTree lastPush = op.createPushCountCode(vars);
        if (lastPush != null) {
            b.startAssign(vars.lastChildPushCount).tree(lastPush).end();
        }

        if (op.needsOperationData()) {
            b.startAssign(vars.operationData).variable(vars.operationData).string(".parent").end();
        }

        if (op.isRealOperation()) {
            b.statement("doAfterChild()");
        }

        vars.numChildren = null;

        return metEnd;
    }

    private CodeExecutableElement createBeginOperation(CodeTypeElement typBuilder, BuilderVariables vars, Operation op) {
        CodeExecutableElement metBegin = GeneratorUtils.overrideImplement(typBuilder, "begin" + op.name);
        GeneratorUtils.addSuppressWarnings(context, metBegin, "unused");

        // doBeforeChild();

        // operationData = new ...(operationData, ID, <x>, args...);

        // << begin >>

        CodeTreeBuilder b = metBegin.getBuilder();

        if (op instanceof InstrumentTag) {
            b.startIf().string("!withInstrumentation").end().startBlock().returnStatement().end();
        }

        if (FLAG_NODE_AST_PRINTING) {
            b.statement("System.out.print(\"\\n\" + \" \".repeat(indent) + \"(" + op.name + "\")");
            b.statement("indent++");
        }

        if (op.isRealOperation()) {
            b.statement("doBeforeChild()");
        }

        if (op.needsOperationData()) {
            b.startAssign(vars.operationData).startNew(types.BuilderOperationData);

            b.variable(vars.operationData);
            b.variable(op.idConstantField);
            b.string("getCurStack()");
            b.string("" + op.getNumAuxValues());
            b.string("" + op.hasLeaveCode());

            b.string("" + op.numLocalReferences());

            for (VariableElement el : metBegin.getParameters()) {
                b.startGroup().cast(context.getType(Object.class)).variable(el).end();
            }

            b.end(2);
        }

        b.tree(op.createBeginCode(vars));

        return metBegin;
    }

    private CodeExecutableElement createSetResultUnboxed() {
        CodeExecutableElement mDoSetResultUnboxed = new CodeExecutableElement(MOD_PRIVATE_STATIC, context.getType(void.class), "doSetResultBoxed");

        CodeVariableElement varBc = new CodeVariableElement(arrayOf(context.getType(short.class)), "bc");
        mDoSetResultUnboxed.addParameter(varBc);

        CodeVariableElement varStartBci = new CodeVariableElement(context.getType(int.class), "startBci");
        mDoSetResultUnboxed.addParameter(varStartBci);

        CodeVariableElement varBciOffset = new CodeVariableElement(context.getType(int.class), "bciOffset");
        mDoSetResultUnboxed.addParameter(varBciOffset);

        CodeVariableElement varTargetType = new CodeVariableElement(context.getType(int.class), "targetType");
        mDoSetResultUnboxed.addParameter(varTargetType);

        CodeTreeBuilder b = mDoSetResultUnboxed.createBuilder();

        b.startIf().variable(varBciOffset).string(" != 0").end().startBlock();
        // {
        b.startStatement().startCall("setResultBoxedImpl");
        b.variable(varBc);
        b.string("startBci - bciOffset");
        b.variable(varTargetType);
        b.startGroup().string("BOXING_DESCRIPTORS[").variable(varTargetType).string("]").end();
        b.end(2);
        // }
        b.end();
        return mDoSetResultUnboxed;
    }

    private CodeVariableElement createBoxingDescriptors() {
        CodeVariableElement fldBoxingDescriptors = new CodeVariableElement(MOD_PRIVATE_STATIC_FINAL, arrayOf(arrayOf(context.getType(short.class))), "BOXING_DESCRIPTORS");

        CodeTreeBuilder b = fldBoxingDescriptors.createInitBuilder();

        b.string("{").startCommaGroup();
        for (FrameKind kind : FrameKind.values()) {
            b.startGroup().newLine();
            b.lineComment("" + kind);
            if (m.getFrameKinds().contains(kind)) {
                b.string("{").startCommaGroup();
                b.string("-1");
                for (Instruction instr : m.getInstructions()) {
                    switch (instr.boxingEliminationBehaviour()) {
                        case DO_NOTHING:
                            b.string("0");
                            break;
                        case REPLACE:
                            b.variable(instr.boxingEliminationReplacement(kind));
                            break;
                        case SET_BIT: {
                            b.startGroup();
                            b.cast(context.getType(short.class));
                            b.startParantheses();
                            b.string("0x8000 | ");
                            b.startParantheses().startParantheses().tree(instr.boxingEliminationBitOffset()).end().string(" << 8").end();
                            b.string(" | ");
                            b.string("" + instr.boxingEliminationBitMask());
                            b.end(2);
                            break;
                        }
                        default:
                            throw new UnsupportedOperationException("unknown boxing behaviour: " + instr.boxingEliminationBehaviour());
                    }

                }
                b.end().string("}").end();
            } else {
                b.string("null").end();
            }
        }
        b.end().string("}");
        return fldBoxingDescriptors;
    }

    private CodeExecutableElement createAfterChild(BuilderVariables vars) {
        CodeExecutableElement mAfterChild = new CodeExecutableElement(context.getType(void.class), "doAfterChild");
        CodeTreeBuilder b = mAfterChild.getBuilder();
        GeneratorUtils.addSuppressWarnings(context, mAfterChild, "unused");

        CodeVariableElement varChildIndex = new CodeVariableElement(context.getType(int.class), "childIndex");
        b.declaration("int", varChildIndex.getName(), "operationData.numChildren++");

        vars.childIndex = varChildIndex;

        b.startSwitch().variable(vars.operationData).string(".operationId").end(2);
        b.startBlock();

        for (Operation parentOp : m.getOperations()) {

            CodeTree afterChild = parentOp.createAfterChildCode(vars);
            if (afterChild == null) {
                continue;
            }

            b.startCase().variable(parentOp.idConstantField).end();
            b.startBlock();

            b.tree(afterChild);

            b.statement("break");
            b.end();
        }

        b.end();

        vars.childIndex = null;
        return mAfterChild;
    }

    private CodeExecutableElement createBeforeChild(BuilderVariables vars) {
        CodeExecutableElement mBeforeChild = new CodeExecutableElement(context.getType(void.class), "doBeforeChild");
        CodeTreeBuilder b = mBeforeChild.getBuilder();
        GeneratorUtils.addSuppressWarnings(context, mBeforeChild, "unused");

        CodeVariableElement varChildIndex = new CodeVariableElement(context.getType(int.class), "childIndex");
        b.declaration("int", varChildIndex.getName(), "operationData.numChildren");

        vars.childIndex = varChildIndex;

        b.startSwitch().variable(vars.operationData).string(".operationId").end(2);
        b.startBlock();

        for (Operation parentOp : m.getOperations()) {

            CodeTree afterChild = parentOp.createBeforeChildCode(vars);
            if (afterChild == null) {
                continue;
            }

            b.startCase().variable(parentOp.idConstantField).end();
            b.startBlock();

            b.tree(afterChild);

            b.statement("break");
            b.end();
        }

        b.end();

        vars.childIndex = null;

        return mBeforeChild;
    }

    private CodeExecutableElement createDoLeave(BuilderVariables vars) {
        CodeVariableElement parData = new CodeVariableElement(types.BuilderOperationData, "data");
        CodeExecutableElement mDoLeave = GeneratorUtils.overrideImplement(types.OperationBuilder, "doLeaveOperation");

        CodeTreeBuilder b = mDoLeave.createBuilder();

        b.startSwitch().string("data.operationId").end();
        b.startBlock();

        CodeVariableElement oldOperationData = vars.operationData;

        vars.operationData = parData;

        for (Operation op : m.getOperations()) {
            CodeTree leaveCode = op.createLeaveCode(vars);
            if (leaveCode == null) {
                continue;
            }

            b.startCase().variable(op.idConstantField).end();
            b.startBlock();

            b.tree(leaveCode);
            b.statement("break");

            b.end();

        }

        vars.operationData = oldOperationData;

        b.end();

        return mDoLeave;
    }

    private static TypeMirror arrayOf(TypeMirror el) {
        return new ArrayCodeTypeMirror(el);
    }

    private static TypeMirror consumer(TypeMirror el) {
        return new DeclaredCodeTypeMirror(ProcessorContext.getInstance().getTypeElement(Consumer.class), List.of(el));
    }

    @Override
    @SuppressWarnings("hiding")
    public List<CodeTypeElement> create(ProcessorContext context, AnnotationProcessor<?> processor, OperationsData m) {
        this.context = context;
        this.m = m;

        String simpleName = m.getTemplateType().getSimpleName() + "Builder";

        return List.of(createBuilder(simpleName));
    }

}
