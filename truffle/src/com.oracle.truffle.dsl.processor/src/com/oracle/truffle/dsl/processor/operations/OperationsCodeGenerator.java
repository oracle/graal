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
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.AnnotationProcessor;
import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.generator.CodeTypeElementFactory;
import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.ArrayCodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.DeclaredCodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.operations.Operation.BuilderVariables;
import com.oracle.truffle.dsl.processor.operations.instructions.CustomInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.FrameKind;
import com.oracle.truffle.dsl.processor.operations.instructions.Instruction;

public class OperationsCodeGenerator extends CodeTypeElementFactory<OperationsData> {

    private ProcessorContext context;
    private OperationsData m;

    private static final Set<Modifier> MOD_FINAL = Set.of(Modifier.FINAL);
    private static final Set<Modifier> MOD_PUBLIC = Set.of(Modifier.PUBLIC);
    private static final Set<Modifier> MOD_PUBLIC_ABSTRACT = Set.of(Modifier.PUBLIC, Modifier.ABSTRACT);
    private static final Set<Modifier> MOD_PUBLIC_STATIC = Set.of(Modifier.PUBLIC, Modifier.STATIC);
    private static final Set<Modifier> MOD_PRIVATE = Set.of(Modifier.PRIVATE);
    private static final Set<Modifier> MOD_ABSTRACT = Set.of(Modifier.ABSTRACT);
    private static final Set<Modifier> MOD_PRIVATE_FINAL = Set.of(Modifier.PRIVATE, Modifier.FINAL);
    private static final Set<Modifier> MOD_PRIVATE_STATIC = Set.of(Modifier.PRIVATE, Modifier.STATIC);
    private static final Set<Modifier> MOD_PRIVATE_STATIC_ABSTRACT = Set.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.ABSTRACT);
    private static final Set<Modifier> MOD_PRIVATE_STATIC_FINAL = Set.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);
    private static final Set<Modifier> MOD_PROTECTED = Set.of(Modifier.PROTECTED);
    private static final Set<Modifier> MOD_PROTECTED_STATIC = Set.of(Modifier.PROTECTED, Modifier.STATIC);
    private static final Set<Modifier> MOD_STATIC = Set.of(Modifier.STATIC);

    private OperationsBytecodeCodeGenerator bytecodeGenerator;

    private static final String OPERATION_NODES_IMPL_NAME = "OperationNodesImpl";
    private static final String OPERATION_BUILDER_IMPL_NAME = "BuilderImpl";
    private static final String BYTECODE_BASE_NAME = "BytecodeLoopBase";

    CodeTypeElement createOperationNodes() {
        CodeTypeElement typOperationNodes = GeneratorUtils.createClass(m, null, MOD_PRIVATE_STATIC_FINAL, OPERATION_NODES_IMPL_NAME, types.OperationNodes);
        typOperationNodes.add(GeneratorUtils.createConstructorUsingFields(Set.of(), typOperationNodes));

        CodeExecutableElement metReparse = GeneratorUtils.overrideImplement(types.OperationNodes, "reparseImpl");
        typOperationNodes.add(metReparse);

        CodeTreeBuilder b = metReparse.createBuilder();

        b.statement("BuilderImpl builder = new BuilderImpl(this, true, config)");
        b.statement("((Consumer) parse).accept(builder)");
        b.statement("builder.finish()");

        CodeExecutableElement mSetSources = new CodeExecutableElement(context.getType(void.class), "setSources");
        mSetSources.addParameter(new CodeVariableElement(arrayOf(types.Source), "sources"));
        mSetSources.createBuilder().statement("this.sources = sources");
        typOperationNodes.add(mSetSources);

        CodeExecutableElement mSetNodes = new CodeExecutableElement(context.getType(void.class), "setNodes");
        mSetNodes.addParameter(new CodeVariableElement(arrayOf(types.OperationNode), "nodes"));
        mSetNodes.createBuilder().statement("this.nodes = nodes");
        typOperationNodes.add(mSetNodes);

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

        CodeTypeElement opNodesImpl = createOperationNodes();

        typBuilder.add(opNodesImpl);

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

        typBuilder.add(new CodeExecutableElement(MOD_PUBLIC_ABSTRACT, types.OperationLocal, "createLocal"));
        typBuilder.add(new CodeExecutableElement(MOD_PUBLIC_ABSTRACT, types.OperationLabel, "createLabel"));
        typBuilder.add(new CodeExecutableElement(MOD_PUBLIC_ABSTRACT, types.OperationNode, "publish"));

        CodeTypeElement typBuilderImpl = createBuilderImpl(typBuilder, opNodesImpl);
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

        b.declaration("OperationNodesImpl", "nodes", "new OperationNodesImpl(generator)");
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

    private static CodeVariableElement compFinal(CodeVariableElement el) {
        if (el.getType().getKind() == TypeKind.ARRAY) {
            GeneratorUtils.addCompilationFinalAnnotation(el, 1);
        } else {
            GeneratorUtils.addCompilationFinalAnnotation(el);
        }
        return el;
    }

    private static CodeVariableElement children(CodeVariableElement el) {
        el.addAnnotationMirror(new CodeAnnotationMirror(ProcessorContext.getInstance().getTypes().Node_Children));
        return el;
    }

    CodeTypeElement createOperationNodeImpl(CodeTypeElement typBytecodeBase, CodeTypeElement typExceptionHandler) {
        CodeTypeElement typOperationNodeImpl = GeneratorUtils.createClass(m, null, MOD_PRIVATE_STATIC_FINAL, "OperationNodeImpl", types.OperationNode);

        typOperationNodeImpl.getImplements().add(types.BytecodeOSRNode);

        typOperationNodeImpl.add(GeneratorUtils.createConstructorUsingFields(MOD_PRIVATE, typOperationNodeImpl));

        typOperationNodeImpl.add(compFinal(new CodeVariableElement(context.getType(short[].class), "_bc")));
        typOperationNodeImpl.add(compFinal(new CodeVariableElement(context.getType(Object[].class), "_consts")));
        typOperationNodeImpl.add(children(new CodeVariableElement(arrayOf(types.Node), "_children")));
        typOperationNodeImpl.add(compFinal(new CodeVariableElement(arrayOf(typExceptionHandler.asType()), "_handlers")));
        typOperationNodeImpl.add(compFinal(new CodeVariableElement(context.getType(int[].class), "_conditionProfiles")));
        typOperationNodeImpl.add(compFinal(new CodeVariableElement(context.getType(int.class), "_maxLocals")));
        typOperationNodeImpl.add(compFinal(new CodeVariableElement(context.getType(int.class), "_maxStack")));
        typOperationNodeImpl.add(compFinal(new CodeVariableElement(context.getType(int[].class), "sourceInfo")));

        typOperationNodeImpl.add(compFinal(new CodeVariableElement(MOD_PRIVATE, context.getType(Object.class), "_osrMetadata")));

        if (!m.getMetadatas().isEmpty()) {
            CodeExecutableElement staticInit = new CodeExecutableElement(MOD_STATIC, null, "<cinit>");
            typOperationNodeImpl.add(staticInit);

            CodeTreeBuilder initBuilder = staticInit.createBuilder();

            for (OperationMetadataData metadata : m.getMetadatas()) {
                String fieldName = "_metadata_" + metadata.getName();
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

        typOperationNodeImpl.add(new CodeVariableElement(MOD_PRIVATE_STATIC_FINAL, typBytecodeBase.asType(), "COMMON_EXECUTE = new BytecodeNode()"));
        typOperationNodeImpl.add(new CodeVariableElement(MOD_PRIVATE_STATIC_FINAL, typBytecodeBase.asType(), "INITIAL_EXECUTE = COMMON_EXECUTE"));

        typOperationNodeImpl.add(createNodeImplExecuteAt());

        CodeExecutableElement mExecute = GeneratorUtils.overrideImplement(types.OperationNode, "execute");
        typOperationNodeImpl.add(mExecute);
        mExecute.createBuilder().startReturn().startCall("executeAt").string("frame, _maxLocals << 16").end(2);

        CodeExecutableElement mGetSourceInfo = GeneratorUtils.overrideImplement(types.OperationNode, "getSourceInfo");
        typOperationNodeImpl.add(mGetSourceInfo);
        mGetSourceInfo.createBuilder().statement("return sourceInfo");

        CodeExecutableElement mDump = GeneratorUtils.overrideImplement(types.OperationNode, "dump");
        typOperationNodeImpl.add(mDump);
        mDump.createBuilder().startReturn().startCall("switchImpl.dump").string("_bc, _handlers, _consts").end(2);

        CodeExecutableElement mGetLockAccessor = new CodeExecutableElement(MOD_PRIVATE, context.getType(Lock.class), "getLockAccessor");
        typOperationNodeImpl.add(mGetLockAccessor);
        mGetLockAccessor.createBuilder().startReturn().startCall("getLock").end(2);

        CodeExecutableElement mInsertAccessor = new CodeExecutableElement(MOD_PRIVATE, null, "<T extends Node> T insertAccessor(T node) { // ");
        typOperationNodeImpl.add(mInsertAccessor);
        mInsertAccessor.createBuilder().startReturn().startCall("insert").string("node").end(2);

        typOperationNodeImpl.add(createNodeImplCreateFrameDescriptor());

        CodeExecutableElement mExecuteOSR = GeneratorUtils.overrideImplement(types.BytecodeOSRNode, "executeOSR");
        typOperationNodeImpl.add(mExecuteOSR);
        mExecuteOSR.createBuilder().startReturn().startCall("executeAt").string("osrFrame, target").end(2);

        CodeExecutableElement mGetOSRMetadata = GeneratorUtils.overrideImplement(types.BytecodeOSRNode, "getOSRMetadata");
        typOperationNodeImpl.add(mGetOSRMetadata);
        mGetOSRMetadata.createBuilder().startReturn().string("_osrMetadata").end();

        CodeExecutableElement mSetOSRMetadata = GeneratorUtils.overrideImplement(types.BytecodeOSRNode, "setOSRMetadata");
        typOperationNodeImpl.add(mSetOSRMetadata);
        mSetOSRMetadata.createBuilder().startAssign("_osrMetadata").string("osrMetadata").end();

        typOperationNodeImpl.add(createNodeImplDeepCopy(typOperationNodeImpl));
        typOperationNodeImpl.add(createNodeImplCopy(typOperationNodeImpl));

        return typOperationNodeImpl;
    }

    private CodeExecutableElement createNodeImplDeepCopy(CodeTypeElement typOperationNodeImpl) {
        CodeExecutableElement met = GeneratorUtils.overrideImplement(types.Node, "deepCopy");
        CodeTreeBuilder b = met.createBuilder();

        b.declaration(typOperationNodeImpl.asType(), "result", "new OperationNodeImpl(nodes)");

        b.statement("result._bc = Arrays.copyOf(_bc, _bc.length)");
        b.statement("result._consts = Arrays.copyOf(_consts, _consts.length)");
        b.statement("result._children = Arrays.copyOf(_children, _children.length)");
        b.statement("result._handlers = _handlers");
        b.statement("result._conditionProfiles = Arrays.copyOf(_conditionProfiles, _conditionProfiles.length)");
        b.statement("result._maxLocals = _maxLocals");
        b.statement("result._maxStack = _maxStack");
        b.statement("result.sourceInfo = sourceInfo");

        for (OperationMetadataData metadata : m.getMetadatas()) {
            b.statement("result._metadata_" + metadata.getName() + " = _metadata_" + metadata.getName());
        }

        b.statement("return result");

        return met;
    }

    private CodeExecutableElement createNodeImplCopy(CodeTypeElement typOperationNodeImpl) {
        CodeExecutableElement met = GeneratorUtils.overrideImplement(types.Node, "copy");
        CodeTreeBuilder b = met.createBuilder();

        b.declaration(typOperationNodeImpl.asType(), "result", "new OperationNodeImpl(nodes)");

        b.statement("result._bc = _bc");
        b.statement("result._consts = _consts");
        b.statement("result._children = _children");
        b.statement("result._handlers = _handlers");
        b.statement("result._conditionProfiles = _conditionProfiles");
        b.statement("result._maxLocals = _maxLocals");
        b.statement("result._maxStack = _maxStack");
        b.statement("result.sourceInfo = sourceInfo");

        for (OperationMetadataData metadata : m.getMetadatas()) {
            b.statement("result._metadata_" + metadata.getName() + " = _metadata_" + metadata.getName());
        }

        b.statement("return result");

        return met;
    }

    private CodeExecutableElement createNodeImplCreateFrameDescriptor() {
        CodeExecutableElement mCreateFrameDescriptor = GeneratorUtils.overrideImplement(types.OperationNode, "createFrameDescriptor");
        CodeTreeBuilder b = mCreateFrameDescriptor.createBuilder();
        b.statement("FrameDescriptor.Builder builder = FrameDescriptor.newBuilder()");
        b.statement("builder.addSlots(_maxLocals + _maxStack, FrameSlotKind.Illegal)");
        b.statement("return builder.build()");
        return mCreateFrameDescriptor;
    }

    private CodeExecutableElement createNodeImplExecuteAt() {
        CodeExecutableElement mExecuteAt = new CodeExecutableElement(MOD_PRIVATE, context.getType(Object.class), "executeAt");
        mExecuteAt.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));
        mExecuteAt.addParameter(new CodeVariableElement(context.getType(int.class), "storedLocation"));

        CodeTreeBuilder b = mExecuteAt.createBuilder();
        b.declaration("int", "result", "storedLocation");
        b.startWhile().string("true").end().startBlock();

        b.startAssign("result").startCall("switchImpl", "continueAt");
        b.string("this");
        b.string("frame");
        b.string("_bc");
        b.string("result & 0xffff");
        b.string("(result >> 16) & 0xffff");
        b.string("_consts");
        b.string("_children");
        b.string("_handlers");
        b.string("_conditionProfiles");
        b.string("_maxLocals");
        b.end(2);

        b.startIf().string("(result & 0xffff) == 0xffff").end().startBlock();
        b.statement("break");
        b.end().startElseBlock();
        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
        b.end();

        b.end();

        b.startReturn().string("frame.getObject((result >> 16) & 0xffff)").end();

        return mExecuteAt;
    }

    private CodeTypeElement createBuilderImpl(CodeTypeElement typBuilder, CodeTypeElement opNodesImpl) {
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

        CodeTypeElement opDataImpl = createOperationDataImpl();
        typBuilderImpl.add(opDataImpl);

        CodeTypeElement typFinallyTryContext = GeneratorUtils.createClass(m, null, MOD_PRIVATE_STATIC_FINAL, "BuilderFinallyTryContext", null);

        CodeTypeElement typLabelData = createOperationLabelImpl(opDataImpl, typFinallyTryContext);
        typBuilderImpl.add(typLabelData);

        CodeTypeElement typLocalData = createOperationLocalImpl(opDataImpl);
        typBuilderImpl.add(typLocalData);

        CodeTypeElement typLabelFill = typBuilderImpl.add(createLabelFill(typLabelData));

        CodeTypeElement typExceptionHandler = typBuilderImpl.add(createExceptionHandler());

        CodeTypeElement typSourceBuilder = createSourceBuilder(typBuilderImpl);
        typBuilderImpl.add(typSourceBuilder);

        createFinallyTryContext(typFinallyTryContext, typExceptionHandler, typLabelFill, typLabelData);
        typBuilderImpl.add(typFinallyTryContext);

        m.getOperationsContext().labelType = typLabelData.asType();
        m.getOperationsContext().exceptionType = typExceptionHandler.asType();

        typBuilderImpl.add(createBuilderImplCtor(opNodesImpl));

        typBuilderImpl.add(new CodeVariableElement(MOD_PRIVATE_FINAL, opNodesImpl.asType(), "nodes"));
        typBuilderImpl.add(new CodeVariableElement(MOD_PRIVATE_FINAL, context.getType(boolean.class), "isReparse"));
        typBuilderImpl.add(new CodeVariableElement(MOD_PRIVATE_FINAL, context.getType(boolean.class), "withSource"));
        typBuilderImpl.add(new CodeVariableElement(MOD_PRIVATE_FINAL, context.getType(boolean.class), "withInstrumentation"));
        typBuilderImpl.add(new CodeVariableElement(MOD_PRIVATE_FINAL, typSourceBuilder.asType(), "sourceBuilder"));

        typBuilderImpl.add(new CodeVariableElement(MOD_PRIVATE, context.getType(short[].class), "bc = new short[65535]"));
        typBuilderImpl.add(new CodeVariableElement(MOD_PRIVATE, context.getType(int.class), "bci"));
        typBuilderImpl.add(new CodeVariableElement(MOD_PRIVATE, context.getType(int.class), "curStack"));
        typBuilderImpl.add(new CodeVariableElement(MOD_PRIVATE, context.getType(int.class), "maxStack"));
        typBuilderImpl.add(new CodeVariableElement(MOD_PRIVATE, context.getType(int.class), "numLocals"));
        CodeVariableElement fldConstPool = typBuilderImpl.add(
                        new CodeVariableElement(MOD_PRIVATE, new DeclaredCodeTypeMirror(context.getTypeElement(ArrayList.class), List.of(context.getType(Object.class))), "constPool"));
        fldConstPool.createInitBuilder().string("new ArrayList<>()");
        typBuilderImpl.add(new CodeVariableElement(MOD_PRIVATE, opDataImpl.asType(), "operationData"));
        typBuilderImpl.add(new CodeVariableElement(MOD_PRIVATE, new DeclaredCodeTypeMirror(context.getTypeElement(ArrayList.class), List.of(typLabelData.asType())), "labels = new ArrayList<>()"));
        typBuilderImpl.add(new CodeVariableElement(MOD_PRIVATE, new DeclaredCodeTypeMirror(context.getTypeElement(ArrayList.class), List.of(typLabelFill.asType())), "labelFills = new ArrayList<>()"));
        typBuilderImpl.add(new CodeVariableElement(MOD_PRIVATE, context.getType(int.class), "numChildNodes"));
        typBuilderImpl.add(new CodeVariableElement(MOD_PRIVATE, context.getType(int.class), "numBranchProfiles"));
        typBuilderImpl.add(new CodeVariableElement(MOD_PRIVATE, new DeclaredCodeTypeMirror(context.getTypeElement(ArrayList.class), List.of(typExceptionHandler.asType())),
                        "exceptionHandlers = new ArrayList<>()"));
        typBuilderImpl.add(new CodeVariableElement(MOD_PRIVATE, typFinallyTryContext.asType(), "currentFinallyTry"));

        typBuilderImpl.add(new CodeVariableElement(MOD_PRIVATE, context.getType(int.class), "buildIndex"));

        typBuilderImpl.add(createBuilderImplFinish());
        typBuilderImpl.add(createBuilderImplReset());

        typBuilderImpl.add(new CodeVariableElement(MOD_PRIVATE, context.getType(int[].class), "stackSourceBci = new int[1024]"));
        typBuilderImpl.add(createBuilderImplDoBeforeEmitInstruction());

        typBuilderImpl.add(createDoLeaveFinallyTry(opDataImpl));
        typBuilderImpl.add(createBuilderImplDoEmitLabel(typLabelData));
        typBuilderImpl.addAll(createBuilderImplCalculateLeaves(opDataImpl, typLabelData));

        typBuilderImpl.add(createBuilderImplCreateLocal(typBuilder, typLocalData));
        typBuilderImpl.add(createBuilderImplCreateParentLocal(typBuilder, typLocalData));
        typBuilderImpl.add(createBuilderImplCreateLabel(typBuilder, typLabelData));
        typBuilderImpl.addAll(createBuilderImplGetLocalIndex(typLocalData));
        typBuilderImpl.add(createBuilderImplVerifyNesting(opDataImpl));
        typBuilderImpl.add(createBuilderImplPublish());
        typBuilderImpl.add(createBuilderImplLabelPass(typFinallyTryContext));

        // operation IDs
        for (Operation op : m.getOperationsContext().operations) {
            CodeVariableElement fldId = new CodeVariableElement(MOD_PRIVATE_STATIC_FINAL, context.getType(int.class), "OP_" + OperationGeneratorUtils.toScreamCase(op.name));
            CodeTreeBuilder b = fldId.createInitBuilder();
            b.string("" + op.id);
            op.setIdConstantField(fldId);
            typBuilderImpl.add(fldId);
        }

        CodeTypeElement bytecodeBaseClass = GeneratorUtils.createClass(m, null, MOD_PRIVATE_STATIC_ABSTRACT, BYTECODE_BASE_NAME, null);

        CodeTypeElement typOperationNodeImpl = createOperationNodeImpl(bytecodeBaseClass, typExceptionHandler);
        typBuilderImpl.add(typOperationNodeImpl);

        createBytecodeBaseClass(bytecodeBaseClass, typOperationNodeImpl, typExceptionHandler);
        typBuilderImpl.add(bytecodeBaseClass);

        typBuilderImpl.add(new CodeVariableElement(MOD_PRIVATE_FINAL, new DeclaredCodeTypeMirror(context.getTypeElement(ArrayList.class), List.of(typOperationNodeImpl.asType())), "builtNodes"));

        CodeVariableElement fldSwitchImpl = new CodeVariableElement(MOD_PRIVATE, bytecodeBaseClass.asType(), "switchImpl");
        GeneratorUtils.addCompilationFinalAnnotation(fldSwitchImpl);
        fldSwitchImpl.createInitBuilder().string("INITIAL_EXECUTE");
        typOperationNodeImpl.add(fldSwitchImpl);

        CodeTypeElement builderBytecodeNodeType;
        CodeTypeElement builderInstrBytecodeNodeType;

        bytecodeGenerator = new OperationsBytecodeCodeGenerator(typBuilderImpl, bytecodeBaseClass, typOperationNodeImpl, typExceptionHandler, m, false);
        builderBytecodeNodeType = bytecodeGenerator.createBuilderBytecodeNode();
        typBuilderImpl.add(builderBytecodeNodeType);

        if (OperationGeneratorFlags.ENABLE_INSTRUMENTATION) {
            OperationsBytecodeCodeGenerator bcg = new OperationsBytecodeCodeGenerator(typBuilderImpl, bytecodeBaseClass, typOperationNodeImpl, typExceptionHandler, m, true);
            builderInstrBytecodeNodeType = bcg.createBuilderBytecodeNode();
            typBuilderImpl.add(builderInstrBytecodeNodeType);
        } else {
            builderInstrBytecodeNodeType = null;
        }

        CodeVariableElement fldOperationData = new CodeVariableElement(opDataImpl.asType(), "operationData");

        CodeVariableElement fldBc = new CodeVariableElement(arrayOf(context.getType(byte.class)), "bc");

        CodeVariableElement fldIndent = null;
        if (OperationGeneratorFlags.FLAG_NODE_AST_PRINTING) {
            fldIndent = new CodeVariableElement(MOD_PRIVATE, context.getType(int.class), "indent");
            typBuilderImpl.add(fldIndent);
        }

        CodeVariableElement fldBci = new CodeVariableElement(context.getType(int.class), "bci");

        CodeVariableElement fldLastPush = new CodeVariableElement(context.getType(int.class), "lastChildPush");
        typBuilderImpl.add(fldLastPush);

        BuilderVariables vars = new BuilderVariables();
        vars.bc = fldBc;
        vars.bci = fldBci;
        vars.operationData = fldOperationData;
        vars.lastChildPushCount = fldLastPush;
        vars.consts = fldConstPool;

        typBuilderImpl.add(createDoLeave(vars, opDataImpl));
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

        return typBuilderImpl;

    }

    private List<CodeExecutableElement> createBuilderImplGetLocalIndex(CodeTypeElement typLocalData) {
        CodeExecutableElement mGetLocalIndex = new CodeExecutableElement(MOD_PRIVATE, context.getType(short.class), "getLocalIndex");
        mGetLocalIndex.addParameter(new CodeVariableElement(context.getType(Object.class), "value"));
        CodeTreeBuilder b = mGetLocalIndex.createBuilder();
        b.declaration(typLocalData.asType(), "local", "(" + typLocalData.getSimpleName() + ") value");
        b.startAssert().string("verifyNesting(local.owner, operationData) : \"local access not nested properly\"").end();
        b.startReturn().string("(short) local.id").end();

        CodeExecutableElement mGetLocalIndices = new CodeExecutableElement(MOD_PRIVATE, context.getType(int[].class), "getLocalIndices");
        mGetLocalIndices.addParameter(new CodeVariableElement(context.getType(Object.class), "value"));
        b = mGetLocalIndices.createBuilder();
        b.declaration(arrayOf(types.OperationLocal), "locals", "(OperationLocal[]) value");
        b.declaration("int[]", "result", "new int[locals.length]");
        b.startFor().string("int i = 0; i < locals.length; i++").end().startBlock();
        b.statement("result[i] = getLocalIndex(locals[i])");
        b.end();
        b.statement("return result");

        return List.of(mGetLocalIndex, mGetLocalIndices);
    }

    private CodeExecutableElement createBuilderImplVerifyNesting(CodeTypeElement opDataImpl) {
        CodeExecutableElement mDoEmitLabel = new CodeExecutableElement(MOD_PRIVATE, context.getType(boolean.class), "verifyNesting");
        mDoEmitLabel.addParameter(new CodeVariableElement(opDataImpl.asType(), "parent"));
        mDoEmitLabel.addParameter(new CodeVariableElement(opDataImpl.asType(), "child"));

        CodeTreeBuilder b = mDoEmitLabel.createBuilder();

        b.declaration(opDataImpl.asType(), "cur", "child");
        b.startWhile().string("cur.depth > parent.depth").end().startBlock();
        b.statement("cur = cur.parent");
        b.end();

        b.statement("return cur == parent");

        return mDoEmitLabel;
    }

    private CodeExecutableElement createBuilderImplDoEmitLabel(CodeTypeElement typLabelData) {
        CodeExecutableElement mDoEmitLabel = new CodeExecutableElement(MOD_PRIVATE, context.getType(void.class), "doEmitLabel");
        mDoEmitLabel.addParameter(new CodeVariableElement(types.OperationLabel, "label"));

        CodeTreeBuilder b = mDoEmitLabel.createBuilder();

        b.declaration(typLabelData.asType(), "lbl", "(" + typLabelData.getSimpleName() + ") label");

        b.startIf().string("lbl.hasValue").end().startBlock();
        b.startThrow().startNew(context.getType(UnsupportedOperationException.class)).doubleQuote("label already emitted").end(2);
        b.end();

        b.startIf().string("operationData != lbl.data").end().startBlock();
        b.startThrow().startNew(context.getType(UnsupportedOperationException.class)).doubleQuote("label must be created and emitted inside same opeartion").end(2);
        b.end();

        b.statement("lbl.hasValue = true");
        b.statement("lbl.targetBci = bci");

        return mDoEmitLabel;
    }

    private List<CodeExecutableElement> createBuilderImplCalculateLeaves(CodeTypeElement opDataImpl, CodeTypeElement typLabelData) {
        CodeExecutableElement mCalculateLeaves = new CodeExecutableElement(MOD_PRIVATE, context.getType(void.class), "calculateLeaves");
        mCalculateLeaves.addParameter(new CodeVariableElement(opDataImpl.asType(), "fromData"));
        mCalculateLeaves.addParameter(new CodeVariableElement(opDataImpl.asType(), "toData"));

        CodeTreeBuilder b = mCalculateLeaves.createBuilder();

        b.startIf().string("toData != null && fromData.depth < toData.depth").end().startBlock();
        b.startThrow().startNew(context.getType(UnsupportedOperationException.class)).doubleQuote("illegal jump to deeper operation").end(2);
        b.end();

        b.startIf().string("fromData == toData").end().startBlock().returnStatement().end();

        b.declaration(opDataImpl.asType(), "cur", "fromData");

        b.startWhile().string("true").end().startBlock();

        b.statement("doLeaveOperation(cur)");
        b.statement("cur = cur.parent");

        b.startIf().string("toData == null && cur == null").end().startBlock();
        b.statement("break");
        b.end().startElseIf().string("toData != null && cur.depth <= toData.depth").end();
        b.statement("break");
        b.end();

        b.end();

        b.startIf().string("cur != toData").end();
        b.startThrow().startNew(context.getType(UnsupportedOperationException.class)).doubleQuote("illegal jump to non-parent operation").end(2);
        b.end();

        CodeExecutableElement oneArg = new CodeExecutableElement(MOD_PRIVATE, context.getType(void.class), "calculateLeaves");
        oneArg.addParameter(new CodeVariableElement(opDataImpl.asType(), "fromData"));
        oneArg.createBuilder().statement("calculateLeaves(fromData, (BuilderOperationData) null)");

        CodeExecutableElement labelArg = new CodeExecutableElement(MOD_PRIVATE, context.getType(void.class), "calculateLeaves");
        labelArg.addParameter(new CodeVariableElement(opDataImpl.asType(), "fromData"));
        labelArg.addParameter(new CodeVariableElement(context.getType(Object.class), "toLabel"));
        labelArg.createBuilder().statement("calculateLeaves(fromData, ((OperationLabelImpl) toLabel).data)");

        return List.of(mCalculateLeaves, oneArg, labelArg);
    }

    private CodeTypeElement createOperationLabelImpl(CodeTypeElement opDataImpl, CodeTypeElement typFTC) {
        CodeTypeElement typOperationLabel = GeneratorUtils.createClass(m, null, MOD_PRIVATE_STATIC_FINAL, "OperationLabelImpl", types.OperationLabel);

        typOperationLabel.add(new CodeVariableElement(opDataImpl.asType(), "data"));
        typOperationLabel.add(new CodeVariableElement(typFTC.asType(), "finallyTry"));

        typOperationLabel.add(GeneratorUtils.createConstructorUsingFields(Set.of(), typOperationLabel));

        typOperationLabel.add(new CodeVariableElement(context.getType(int.class), "targetBci = 0"));
        typOperationLabel.add(new CodeVariableElement(context.getType(boolean.class), "hasValue = false"));

        CodeExecutableElement mBelongsTo = typOperationLabel.add(new CodeExecutableElement(context.getType(boolean.class), "belongsTo"));
        mBelongsTo.addParameter(new CodeVariableElement(typFTC.asType(), "context"));
        CodeTreeBuilder b = mBelongsTo.createBuilder();
        b.declaration(typFTC.asType(), "cur", "finallyTry");
        b.startWhile().string("cur != null").end().startBlock(); // {

        b.startIf().string("cur == context").end().startBlock().returnTrue().end();
        b.statement("cur = cur.prev");

        b.end(); // }

        b.returnFalse();

        return typOperationLabel;
    }

    private CodeTypeElement createOperationLocalImpl(CodeTypeElement opDataImpl) {
        CodeTypeElement typLocalData = GeneratorUtils.createClass(m, null, MOD_PRIVATE_STATIC_FINAL, "OperationLocalImpl", types.OperationLocal);

        typLocalData.add(new CodeVariableElement(MOD_FINAL, opDataImpl.asType(), "owner"));
        typLocalData.add(new CodeVariableElement(MOD_FINAL, context.getType(int.class), "id"));

        typLocalData.add(GeneratorUtils.createConstructorUsingFields(Set.of(), typLocalData));

        return typLocalData;
    }

    @SuppressWarnings("static-method")
    private CodeExecutableElement createBuilderImplCreateLocal(CodeTypeElement typBuilder, CodeTypeElement typLocalData) {
        CodeExecutableElement mCreateLocal = GeneratorUtils.overrideImplement(typBuilder, "createLocal");
        mCreateLocal.createBuilder().startReturn().startNew(typLocalData.asType()).string("operationData").string("numLocals++").end(2);
        return mCreateLocal;
    }

    @SuppressWarnings("static-method")
    private CodeExecutableElement createBuilderImplCreateParentLocal(CodeTypeElement typBuilder, CodeTypeElement typLocalData) {
        CodeExecutableElement mCreateLocal = new CodeExecutableElement(typLocalData.asType(), "createParentLocal");
        mCreateLocal.createBuilder().startReturn().startNew(typLocalData.asType()).string("operationData.parent").string("numLocals++").end(2);
        return mCreateLocal;
    }

    @SuppressWarnings("static-method")
    private CodeExecutableElement createBuilderImplCreateLabel(CodeTypeElement typBuilder, CodeTypeElement typLabelData) {
        CodeExecutableElement mCreateLocal = GeneratorUtils.overrideImplement(typBuilder, "createLabel");

        CodeTreeBuilder b = mCreateLocal.createBuilder();
        b.startAssign("OperationLabelImpl label").startNew(typLabelData.asType()).string("operationData").string("currentFinallyTry").end(2);
        b.startStatement().startCall("labels", "add").string("label").end(2);
        b.startReturn().string("label").end();

        return mCreateLocal;
    }

    private CodeTypeElement createExceptionHandler() {
        CodeTypeElement typ = GeneratorUtils.createClass(m, null, MOD_PRIVATE_STATIC_FINAL, "ExceptionHandler", null);

        typ.add(new CodeVariableElement(context.getType(int.class), "startBci"));
        typ.add(new CodeVariableElement(context.getType(int.class), "startStack"));
        typ.add(new CodeVariableElement(context.getType(int.class), "endBci"));
        typ.add(new CodeVariableElement(context.getType(int.class), "exceptionIndex"));
        typ.add(new CodeVariableElement(context.getType(int.class), "handlerBci"));

        typ.add(GeneratorUtils.createConstructorUsingFields(Set.of(), typ));

        typ.add(new CodeExecutableElement(null, typ.getSimpleName().toString()));

        CodeExecutableElement metOffset = typ.add(new CodeExecutableElement(typ.asType(), "offset"));
        metOffset.addParameter(new CodeVariableElement(context.getType(int.class), "offset"));
        metOffset.addParameter(new CodeVariableElement(context.getType(int.class), "stackOffset"));

        CodeTreeBuilder b = metOffset.createBuilder();
        b.startReturn().startNew(typ.asType());
        b.string("startBci + offset");
        b.string("startStack + stackOffset");
        b.string("endBci + offset");
        b.string("exceptionIndex");
        b.string("handlerBci + offset");
        b.end(2);

        CodeExecutableElement mToString = typ.add(GeneratorUtils.override(context.getDeclaredType(Object.class), "toString"));

        b = mToString.createBuilder();
        b.startReturn().startCall("String.format");
        b.doubleQuote("handler {start=%04x, end=%04x, stack=%d, local=%d, handler=%04x}");
        b.string("startBci").string("endBci").string("startStack").string("exceptionIndex").string("handlerBci");
        b.end(2);

        return typ;
    }

    // -------------------------------- source builder ----------------------------

    private CodeTypeElement createSourceBuilder(CodeTypeElement typBuilderImpl) {
        CodeTypeElement typSourceBuilder = GeneratorUtils.createClass(m, null, MOD_PRIVATE_STATIC_FINAL, "SourceInfoBuilder", null);
        typSourceBuilder.setEnclosingElement(typBuilderImpl);

        CodeTypeElement typSourceData = GeneratorUtils.createClass(m, null, MOD_PRIVATE_STATIC_FINAL, "SourceData", null);
        typSourceBuilder.add(typSourceData);
        typSourceData.add(new CodeVariableElement(MOD_FINAL, context.getType(int.class), "start"));
        typSourceData.add(new CodeVariableElement(MOD_FINAL, context.getType(int.class), "length"));
        typSourceData.add(new CodeVariableElement(MOD_FINAL, context.getType(int.class), "sourceIndex"));
        typSourceData.add(GeneratorUtils.createConstructorUsingFields(Set.of(), typSourceData));

        typSourceBuilder.add(new CodeVariableElement(MOD_PRIVATE_FINAL, generic(ArrayList.class, context.getType(Integer.class)), "sourceStack = new ArrayList<>()"));
        typSourceBuilder.add(new CodeVariableElement(MOD_PRIVATE_FINAL, generic(ArrayList.class, types.Source), "sourceList = new ArrayList<>()"));
        typSourceBuilder.add(new CodeVariableElement(MOD_PRIVATE, context.getType(int.class), "currentSource = -1"));
        typSourceBuilder.add(new CodeVariableElement(MOD_PRIVATE_FINAL, generic(ArrayList.class, context.getType(Integer.class)), "bciList = new ArrayList<>()"));
        typSourceBuilder.add(new CodeVariableElement(MOD_PRIVATE_FINAL, generic(ArrayList.class, typSourceData.asType()), "sourceDataList = new ArrayList<>()"));
        typSourceBuilder.add(new CodeVariableElement(MOD_PRIVATE_FINAL, generic(ArrayList.class, typSourceData.asType()), "sourceDataStack = new ArrayList<>()"));

        typSourceBuilder.add(createSourceBuilderReset());
        typSourceBuilder.add(createSourceBuilderBeginSource());
        typSourceBuilder.add(createSourceBuilderEndSource());
        typSourceBuilder.add(createSourceBuilderBeginSourceSection());
        typSourceBuilder.add(createSourceBuilderEndSourceSection());
        typSourceBuilder.add(createSourceBuilderBuild());
        typSourceBuilder.add(createSourceBuilderBuildSource());

        return typSourceBuilder;
    }

    private CodeExecutableElement createSourceBuilderReset() {
        CodeExecutableElement mReset = new CodeExecutableElement(context.getType(void.class), "reset");

        CodeTreeBuilder b = mReset.createBuilder();
        b.statement("sourceStack.clear()");
        b.statement("sourceDataList.clear()");
        b.statement("sourceDataStack.clear()");
        b.statement("bciList.clear()");
        return mReset;
    }

    private CodeExecutableElement createSourceBuilderBeginSource() {
        CodeExecutableElement mBeginSource = new CodeExecutableElement(context.getType(void.class), "beginSource");
        mBeginSource.addParameter(new CodeVariableElement(context.getType(int.class), "bci"));
        mBeginSource.addParameter(new CodeVariableElement(types.Source, "src"));

        CodeTreeBuilder b = mBeginSource.createBuilder();
        b.statement("int idx = sourceList.indexOf(src)");

        b.startIf().string("idx == -1").end().startBlock();
        b.statement("idx = sourceList.size()");
        b.statement("sourceList.add(src)");
        b.end();

        b.statement("sourceStack.add(currentSource)");
        b.statement("currentSource = idx");
        b.statement("beginSourceSection(bci, -1, -1)");
        return mBeginSource;
    }

    private CodeExecutableElement createSourceBuilderEndSource() {
        CodeExecutableElement mEndSource = new CodeExecutableElement(context.getType(void.class), "endSource");
        mEndSource.addParameter(new CodeVariableElement(context.getType(int.class), "bci"));

        CodeTreeBuilder b = mEndSource.createBuilder();
        b.statement("endSourceSection(bci)");
        b.statement("currentSource = sourceStack.remove(sourceStack.size() - 1)");
        return mEndSource;
    }

    private CodeExecutableElement createSourceBuilderBeginSourceSection() {
        CodeExecutableElement mBeginSource = new CodeExecutableElement(context.getType(void.class), "beginSourceSection");
        mBeginSource.addParameter(new CodeVariableElement(context.getType(int.class), "bci"));
        mBeginSource.addParameter(new CodeVariableElement(context.getType(int.class), "start"));
        mBeginSource.addParameter(new CodeVariableElement(context.getType(int.class), "length"));

        CodeTreeBuilder b = mBeginSource.createBuilder();
        b.statement("SourceData data = new SourceData(start, length, currentSource)");
        b.statement("bciList.add(bci)");
        b.statement("sourceDataList.add(data)");
        b.statement("sourceDataStack.add(data)");
        return mBeginSource;
    }

    private CodeExecutableElement createSourceBuilderEndSourceSection() {
        CodeExecutableElement mEndSource = new CodeExecutableElement(context.getType(void.class), "endSourceSection");
        mEndSource.addParameter(new CodeVariableElement(context.getType(int.class), "bci"));

        CodeTreeBuilder b = mEndSource.createBuilder();
        b.statement("SourceData data = sourceDataStack.remove(sourceDataStack.size() - 1)");
        b.statement("SourceData prev");

        b.startIf().string("sourceDataStack.isEmpty()").end().startBlock();
        b.statement("prev = new SourceData(-1, -1, currentSource)");
        b.end().startElseBlock();
        b.statement("prev = sourceDataStack.get(sourceDataStack.size() - 1)");
        b.end();

        b.statement("bciList.add(bci)");
        b.statement("sourceDataList.add(prev)");
        return mEndSource;
    }

    private CodeExecutableElement createSourceBuilderBuildSource() {
        CodeExecutableElement mEndSource = new CodeExecutableElement(arrayOf(types.Source), "buildSource");

        CodeTreeBuilder b = mEndSource.createBuilder();
        b.statement("return sourceList.toArray(new Source[0])");

        return mEndSource;
    }

    private CodeExecutableElement createSourceBuilderBuild() {
        CodeExecutableElement mEndSource = new CodeExecutableElement(context.getType(int[].class), "build");

        CodeTreeBuilder b = mEndSource.createBuilder();

        b.startIf().string("!sourceStack.isEmpty()").end().startBlock();
        b.startThrow().startNew(context.getType(IllegalStateException.class)).doubleQuote("not all sources ended").end(2);
        b.end();

        b.startIf().string("!sourceDataStack.isEmpty()").end().startBlock();
        b.startThrow().startNew(context.getType(IllegalStateException.class)).doubleQuote("not all source sections ended").end(2);
        b.end();

        int sourceStride = 3;

        b.statement("int size = bciList.size()");

        b.statement("int[] resultArray = new int[size * " + sourceStride + "]");

        b.statement("int index = 0");
        b.statement("int lastBci = -1");
        b.statement("boolean isFirst = true");

        b.startFor().string("int i = 0; i < size; i++").end().startBlock();
        b.statement("SourceData data = sourceDataList.get(i)");
        b.statement("int curBci = bciList.get(i)");

        b.startIf().string("data.start == -1 && isFirst").end().startBlock().statement("continue").end();

        b.statement("isFirst = false");

        b.startIf().string("curBci == lastBci && index > 1").end().startBlock();
        b.statement("index -= " + sourceStride);
        b.end();

        b.statement("resultArray[index + 0] = curBci | (data.sourceIndex << 16)");
        b.statement("resultArray[index + 1] = data.start");
        b.statement("resultArray[index + 2] = data.length");

        b.statement("index += " + sourceStride);
        b.statement("lastBci = curBci");

        b.end();

        b.statement("return Arrays.copyOf(resultArray, index)");

        return mEndSource;
    }
    // ------------------------------ operadion data impl ------------------------

    private CodeTypeElement createOperationDataImpl() {
        CodeTypeElement opDataImpl = GeneratorUtils.createClass(m, null, MOD_PRIVATE_STATIC_FINAL, "BuilderOperationData", null);
        opDataImpl.add(new CodeVariableElement(MOD_FINAL, opDataImpl.asType(), "parent"));
        opDataImpl.add(new CodeVariableElement(MOD_FINAL, context.getType(int.class), "operationId"));
        opDataImpl.add(new CodeVariableElement(MOD_FINAL, context.getType(int.class), "stackDepth"));

        CodeExecutableElement ctor = opDataImpl.add(GeneratorUtils.createConstructorUsingFields(MOD_PRIVATE, opDataImpl));
        ctor.addParameter(new CodeVariableElement(context.getType(int.class), "numAux"));
        ctor.addParameter(new CodeVariableElement(context.getType(boolean.class), "needsLeave"));
        ctor.addParameter(new CodeVariableElement(context.getType(Object.class), "...arguments"));

        CodeTreeBuilder b = ctor.appendBuilder();
        b.statement("this.depth = parent == null ? 0 : parent.depth + 1");
        b.statement("this.aux = numAux > 0 ? new Object[numAux] : null");
        b.statement("this.needsLeave = needsLeave || (parent != null && parent.needsLeave)");
        b.statement("this.arguments = arguments");

        opDataImpl.add(new CodeVariableElement(MOD_FINAL, context.getType(boolean.class), "needsLeave"));
        opDataImpl.add(new CodeVariableElement(MOD_FINAL, context.getType(int.class), "depth"));
        opDataImpl.add(new CodeVariableElement(MOD_FINAL, context.getType(Object[].class), "arguments"));
        opDataImpl.add(new CodeVariableElement(MOD_FINAL, context.getType(Object[].class), "aux"));
        opDataImpl.add(new CodeVariableElement(context.getType(int.class), "numChildren"));

        return opDataImpl;
    }

    private CodeTypeElement createBytecodeBaseClass(CodeTypeElement baseClass, CodeTypeElement opNodeImpl, CodeTypeElement typExceptionHandler) {

        CodeExecutableElement loopMethod = new CodeExecutableElement(MOD_ABSTRACT, context.getType(int.class), "continueAt");
        loopMethod.addParameter(new CodeVariableElement(opNodeImpl.asType(), "$this"));
        loopMethod.addParameter(new CodeVariableElement(types.VirtualFrame, "$frame"));
        loopMethod.addParameter(new CodeVariableElement(context.getType(short[].class), "$bc"));
        loopMethod.addParameter(new CodeVariableElement(context.getType(int.class), "$startBci"));
        loopMethod.addParameter(new CodeVariableElement(context.getType(int.class), "$startSp"));
        loopMethod.addParameter(new CodeVariableElement(context.getType(Object[].class), "$consts"));
        loopMethod.addParameter(new CodeVariableElement(new ArrayCodeTypeMirror(types.Node), "$children"));
        loopMethod.addParameter(new CodeVariableElement(new ArrayCodeTypeMirror(typExceptionHandler.asType()), "$handlers"));
        loopMethod.addParameter(new CodeVariableElement(context.getType(int[].class), "$conditionProfiles"));
        loopMethod.addParameter(new CodeVariableElement(context.getType(int.class), "maxLocals"));
        baseClass.add(loopMethod);

        CodeExecutableElement dumpMethod = new CodeExecutableElement(MOD_ABSTRACT, context.getType(String.class), "dump");
        dumpMethod.addParameter(new CodeVariableElement(new ArrayCodeTypeMirror(context.getType(short.class)), "$bc"));
        dumpMethod.addParameter(new CodeVariableElement(new ArrayCodeTypeMirror(typExceptionHandler.asType()), "$handlers"));
        dumpMethod.addParameter(new CodeVariableElement(context.getType(Object[].class), "$consts"));
        baseClass.add(dumpMethod);

        if (m.isGenerateAOT()) {
            CodeExecutableElement prepareAot = new CodeExecutableElement(MOD_ABSTRACT, context.getType(void.class), "prepareForAOT");
            prepareAot.addParameter(new CodeVariableElement(opNodeImpl.asType(), "$this"));
            prepareAot.addParameter(new CodeVariableElement(context.getType(short[].class), "$bc"));
            prepareAot.addParameter(new CodeVariableElement(context.getType(Object[].class), "$consts"));
            prepareAot.addParameter(new CodeVariableElement(new ArrayCodeTypeMirror(types.Node), "$children"));
            prepareAot.addParameter(new CodeVariableElement(types.TruffleLanguage, "language"));
            prepareAot.addParameter(new CodeVariableElement(types.RootNode, "root"));
            baseClass.add(prepareAot);
        }

        baseClass.add(createFormatConstant());
        baseClass.add(createExpectObject());
        baseClass.add(createSetResultBoxedImpl());
        for (TypeKind kind : m.getBoxingEliminatedTypes()) {
            FrameKind frameKind = FrameKind.valueOfPrimitive(kind);
            baseClass.add(createExpectPrimitive(frameKind));
            baseClass.add(createStoreLocalCheck(frameKind));
        }

        return baseClass;
    }

    private CodeTypeElement createLabelFill(CodeTypeElement typLabelImpl) {
        CodeTypeElement typ = GeneratorUtils.createClass(m, null, MOD_PRIVATE_STATIC_FINAL, "LabelFill", null);
        typ.add(new CodeVariableElement(context.getType(int.class), "locationBci"));
        typ.add(new CodeVariableElement(typLabelImpl.asType(), "label"));

        typ.add(GeneratorUtils.createConstructorUsingFields(Set.of(), typ));

        CodeExecutableElement metOffset = typ.add(new CodeExecutableElement(typ.asType(), "offset"));
        metOffset.addParameter(new CodeVariableElement(context.getType(int.class), "offset"));

        metOffset.createBuilder().statement("return new LabelFill(offset + locationBci, label)");

        return typ;
    }

    private CodeTypeElement createFinallyTryContext(CodeTypeElement typFtc, CodeTypeElement typExceptionHandler, CodeTypeElement typLabelFill, CodeTypeElement typLabelImpl) {
        typFtc.add(new CodeVariableElement(MOD_FINAL, typFtc.asType(), "prev"));
        typFtc.add(new CodeVariableElement(MOD_FINAL, context.getType(short[].class), "bc"));
        typFtc.add(new CodeVariableElement(MOD_FINAL, generic(ArrayList.class, typExceptionHandler.asType()), "exceptionHandlers"));
        typFtc.add(new CodeVariableElement(MOD_FINAL, generic(ArrayList.class, typLabelFill.asType()), "labelFills"));
        typFtc.add(new CodeVariableElement(MOD_FINAL, generic(ArrayList.class, typLabelImpl.asType()), "labels"));
        typFtc.add(new CodeVariableElement(MOD_FINAL, context.getType(int.class), "curStack"));
        typFtc.add(new CodeVariableElement(MOD_FINAL, context.getType(int.class), "maxStack"));

        typFtc.add(GeneratorUtils.createConstructorUsingFields(Set.of(), typFtc));

        typFtc.add(new CodeVariableElement(Set.of(), context.getType(short[].class), "handlerBc"));
        typFtc.add(new CodeVariableElement(Set.of(), generic(ArrayList.class, typExceptionHandler.asType()), "handlerHandlers"));
        typFtc.add(new CodeVariableElement(Set.of(), generic(ArrayList.class, typLabelFill.asType()), "handlerLabelFills = new ArrayList<>()"));
        typFtc.add(new CodeVariableElement(Set.of(), generic(ArrayList.class, context.getType(Integer.class)), "relocationOffsets = new ArrayList<>()"));
        typFtc.add(new CodeVariableElement(Set.of(), context.getType(int.class), "handlerMaxStack"));

        return typFtc;
    }

    private CodeExecutableElement createDoLeaveFinallyTry(CodeTypeElement typOperationData) {
        CodeExecutableElement mDoLeaveFinallyTry = new CodeExecutableElement(MOD_PRIVATE, context.getType(void.class), "doLeaveFinallyTry",
                        new CodeVariableElement(typOperationData.asType(), "opData"));

        CodeTreeBuilder b = mDoLeaveFinallyTry.createBuilder();
        b.statement("BuilderFinallyTryContext context = (BuilderFinallyTryContext) opData.aux[0]");

        b.startIf().string("context.handlerBc == null").end().startBlock().returnStatement().end();

        b.statement("System.arraycopy(context.handlerBc, 0, bc, bci, context.handlerBc.length)");

        b.startFor().string("int offset : context.relocationOffsets").end().startBlock();
        b.statement("short oldOffset = bc[bci + offset]");
        b.statement("bc[bci + offset] = (short) (oldOffset + bci)");
        b.end();

        b.startFor().string("ExceptionHandler handler : context.handlerHandlers").end().startBlock();
        b.statement("exceptionHandlers.add(handler.offset(bci, curStack))");
        b.end();

        b.startFor().string("LabelFill fill : context.handlerLabelFills").end().startBlock();
        b.statement("labelFills.add(fill.offset(bci))");
        b.end();

        b.startIf().string("maxStack < curStack + context.handlerMaxStack").end();
        b.statement("maxStack = curStack + context.handlerMaxStack");
        b.end();

        b.statement("bci += context.handlerBc.length");

        return mDoLeaveFinallyTry;
    }

    private CodeExecutableElement createMetadataSet(CodeTypeElement typOperationNodeImpl) {
        CodeExecutableElement method = GeneratorUtils.overrideImplement(types.OperationBuilder, "assignMetadata");
        CodeTreeBuilder b = method.createBuilder();

        b.startAssign(typOperationNodeImpl.getSimpleName() + " nodeImpl").cast(typOperationNodeImpl.asType()).string("node").end();

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

    private CodeExecutableElement createEmitOperation(CodeTypeElement typBuilder, BuilderVariables vars, Operation op) {
        CodeExecutableElement metEmit = GeneratorUtils.overrideImplement(typBuilder, "emit" + op.name);
        CodeTreeBuilder b = metEmit.getBuilder();

        if (OperationGeneratorFlags.FLAG_NODE_AST_PRINTING) {
            b.statement("System.out.print(\"\\n\" + \" \".repeat(indent) + \"(" + op.name + ")\")");
        }

        String condition = op.conditionedOn();
        if (condition != null) {
            b.startIf().string(condition).end().startBlock();
        }

        if (op.isRealOperation()) {
            b.statement("doBeforeChild()");
        }

        boolean needsData = op.needsOperationData();

        if (needsData) {
            b.startAssign(vars.operationData);
            b.startNew("BuilderOperationData");

            b.variable(vars.operationData);
            b.variable(op.idConstantField);
            b.string("curStack");
            b.string("" + op.getNumAuxValues());
            b.string("false");

            if (metEmit.getParameters().size() == 1 && metEmit.getParameters().get(0).asType().getKind() == TypeKind.ARRAY) {
                b.startGroup().cast(context.getType(Object.class)).variable(metEmit.getParameters().get(0)).end();
            } else {
                for (VariableElement v : metEmit.getParameters()) {
                    b.variable(v);
                }
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

        if (condition != null) {
            b.end();
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

        String condition = op.conditionedOn();
        if (condition != null) {
            b.startIf().string(condition).end().startBlock();
        }

        if (OperationGeneratorFlags.FLAG_NODE_AST_PRINTING) {
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

        if (condition != null) {
            b.end();
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

        String condition = op.conditionedOn();
        if (condition != null) {
            b.startIf().string(condition).end().startBlock();
        }

        if (OperationGeneratorFlags.FLAG_NODE_AST_PRINTING) {
            b.statement("System.out.print(\"\\n\" + \" \".repeat(indent) + \"(" + op.name + "\")");
            b.statement("indent++");
        }

        if (op.isRealOperation()) {
            b.statement("doBeforeChild()");
        }

        if (op.needsOperationData()) {
            b.startAssign(vars.operationData).startNew("BuilderOperationData");

            b.variable(vars.operationData);
            b.variable(op.idConstantField);
            b.string("curStack");
            b.string("" + op.getNumAuxValues());
            b.string("" + op.hasLeaveCode());

            for (VariableElement el : metBegin.getParameters()) {
                b.startGroup().cast(context.getType(Object.class)).variable(el).end();
            }

            b.end(2);
        }

        b.tree(op.createBeginCode(vars));

        if (condition != null) {
            b.end();
        }

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

    private CodeExecutableElement createBuilderImplPublish() {
        CodeExecutableElement mPublish = new CodeExecutableElement(MOD_PUBLIC, types.OperationNode, "publish");

        CodeTreeBuilder b = mPublish.createBuilder();

        b.startIf().string("operationData.depth != 0").end().startBlock();
        b.startThrow().startNew(context.getType(UnsupportedOperationException.class)).doubleQuote("Not all operations closed").end(2);
        b.end();

        b.declaration("OperationNodeImpl", "result", (CodeTree) null);

        b.startIf().string("!isReparse").end().startBlock();

        b.statement("result = new OperationNodeImpl(nodes)");

        b.statement("labelPass(null)");
        b.statement("result._bc = Arrays.copyOf(bc, bci)");
        b.statement("result._consts = constPool.toArray()");
        b.statement("result._children = new Node[numChildNodes]");
        b.statement("result._handlers = exceptionHandlers.toArray(new ExceptionHandler[0])");
        b.statement("result._conditionProfiles = new int[numBranchProfiles * 2]");
        b.statement("result._maxLocals = numLocals");
        b.statement("result._maxStack = maxStack");

        b.startIf().string("sourceBuilder != null").end().startBlock();
        b.statement("result.sourceInfo = sourceBuilder.build()");
        b.end();

        for (OperationMetadataData metadata : m.getMetadatas()) {
            b.statement("result._metadata_" + metadata.getName() + " = metadata_" + metadata.getName());
        }

        b.statement("assert builtNodes.size() == buildIndex");
        b.statement("builtNodes.add(result)");

        b.end().startElseBlock();

        b.statement("result = builtNodes.get(buildIndex)");

        b.startIf().string("withSource && result.sourceInfo == null").end().startBlock();
        b.statement("result.sourceInfo = sourceBuilder.build()");
        b.end();

        // todo instrumentation

        b.end();

        b.statement("buildIndex++");
        b.statement("reset()");

        b.statement("return result");

        return mPublish;
    }

    private CodeExecutableElement createBuilderImplLabelPass(CodeTypeElement typFtc) {
        CodeExecutableElement mPublish = new CodeExecutableElement(MOD_PRIVATE, context.getType(void.class), "labelPass");
        mPublish.addParameter(new CodeVariableElement(typFtc.asType(), "finallyTry"));

        CodeTreeBuilder b = mPublish.createBuilder();

        b.startFor().string("LabelFill fill : labelFills").end().startBlock(); // {
        b.startIf().string("finallyTry != null").end().startBlock(); // {
        b.startIf().string("fill.label.belongsTo(finallyTry)").end().startBlock(); // {

        b.statement("assert fill.label.hasValue : \"inner label should have been resolved by now\"");
        b.statement("finallyTry.relocationOffsets.add(fill.locationBci)");

        b.end().startElseBlock(); // } {

        b.statement("finallyTry.handlerLabelFills.add(fill)");

        b.end(); // }
        b.end(); // }

        b.statement("bc[fill.locationBci] = (short) fill.label.targetBci");

        b.end(); // }

        return mPublish;

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

    private CodeExecutableElement createDoLeave(BuilderVariables vars, CodeTypeElement typOperationData) {
        CodeVariableElement parData = new CodeVariableElement(typOperationData.asType(), "data");
        CodeExecutableElement mDoLeave = new CodeExecutableElement(MOD_PRIVATE, context.getType(void.class), "doLeaveOperation", parData);

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

    private static TypeMirror generic(TypeElement el, TypeMirror... args) {
        return new DeclaredCodeTypeMirror(el, List.of(args));
    }

    private static TypeMirror generic(Class<?> cls, TypeMirror... args) {
        return generic(ProcessorContext.getInstance().getTypeElement(cls), args);
    }

    @Override
    @SuppressWarnings("hiding")
    public List<CodeTypeElement> create(ProcessorContext context, AnnotationProcessor<?> processor, OperationsData m) {
        this.context = context;
        this.m = m;

        String simpleName = m.getTemplateType().getSimpleName() + "Builder";

        return List.of(createBuilder(simpleName));
    }

    // -------------------------- helper methods moved to generated code --------------------------

    private CodeExecutableElement createExpectObject() {
        CodeExecutableElement mExpectObject = new CodeExecutableElement(MOD_PROTECTED_STATIC, context.getType(Object.class), "expectObject");
        mExpectObject.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));
        mExpectObject.addParameter(new CodeVariableElement(context.getType(int.class), "slot"));

        CodeTreeBuilder b = mExpectObject.createBuilder();

        b.startIf().string("frame.isObject(slot)").end().startBlock(); // if {
        b.startReturn().string("frame.getObject(slot)").end();
        b.end().startElseBlock(); // } else {
        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
        b.startReturn().string("frame.getValue(slot)").end();
        b.end(); // }

        return mExpectObject;
    }

    private CodeExecutableElement createExpectPrimitive(FrameKind kind) {
        CodeExecutableElement mExpectPrimitive = new CodeExecutableElement(MOD_PROTECTED_STATIC, kind.getType(), "expect" + kind.getFrameName());
        mExpectPrimitive.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));
        mExpectPrimitive.addParameter(new CodeVariableElement(context.getType(int.class), "slot"));

        mExpectPrimitive.addThrownType(types.UnexpectedResultException);

        CodeTreeBuilder b = mExpectPrimitive.createBuilder();

        if (OperationGeneratorFlags.LOG_STACK_READS) {
            b.statement("System.out.println(\" [SR] stack read @ \" + slot + \" : \" + frame.getValue(slot) + \"\")");
        }

        b.startSwitch().string("frame.getTag(slot)").end().startBlock(); // switch {

        b.startCase().string(kind.ordinal() + " /* " + kind + " */").end().startCaseBlock(); // {
        b.startReturn().string("frame.get" + kind.getFrameName() + "(slot)").end();
        b.end(); // }

        b.startCase().string(FrameKind.OBJECT.ordinal() + " /* OBJECT */").end().startCaseBlock(); // {
        b.declaration("Object", "value", "frame.getObject(slot)");

        b.startIf().string("value instanceof " + kind.getTypeNameBoxed()).end().startBlock();
        b.startReturn().string("(" + kind.getTypeName() + ") value").end();
        b.end();

        b.statement("break");
        b.end(); // }

        b.end(); // }

        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
        b.startThrow().startNew(types.UnexpectedResultException).string("frame.getValue(slot)").end(2);

        return mExpectPrimitive;
    }

    private CodeExecutableElement createStoreLocalCheck(FrameKind kind) {
        CodeExecutableElement mStoreLocalCheck = new CodeExecutableElement(MOD_PROTECTED_STATIC, context.getType(boolean.class), "storeLocal" + kind.getFrameName() + "Check");

        mStoreLocalCheck.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));
        mStoreLocalCheck.addParameter(new CodeVariableElement(context.getType(int.class), "localSlot"));
        mStoreLocalCheck.addParameter(new CodeVariableElement(context.getType(int.class), "stackSlot"));

        CodeTreeBuilder b = mStoreLocalCheck.createBuilder();

        b.declaration(types.FrameDescriptor, "descriptor", "frame.getFrameDescriptor()");

        b.startIf().string("descriptor.getSlotKind(localSlot) == ").staticReference(types.FrameSlotKind, kind.getFrameName()).end().startBlock();
        b.startTryBlock();
        b.statement("frame.set" + kind.getFrameName() + "(localSlot, expect" + kind.getFrameName() + "(frame, stackSlot))");
        b.returnTrue();
        b.end().startCatchBlock(types.UnexpectedResultException, "ex").end();
        b.end();

        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
        b.statement("descriptor.setSlotKind(localSlot, FrameSlotKind.Object)");
        b.statement("frame.setObject(localSlot, frame.getValue(stackSlot))");
        b.returnFalse();

        return mStoreLocalCheck;
    }

    private CodeExecutableElement createFormatConstant() {
        CodeExecutableElement mFormatConstant = new CodeExecutableElement(MOD_PROTECTED_STATIC, context.getType(String.class), "formatConstant");

        mFormatConstant.addParameter(new CodeVariableElement(context.getType(Object.class), "obj"));

        CodeTreeBuilder b = mFormatConstant.createBuilder();

        b.startIf().string("obj == null").end().startBlock();
        b.startReturn().doubleQuote("null").end();
        b.end().startElseBlock();
        b.declaration("Object", "repr", "obj");

        b.startIf().string("obj instanceof Object[]").end().startBlock();
        b.startAssign("repr").startStaticCall(context.getType(Arrays.class), "deepToString").string("(Object[]) obj").end(2);
        b.end();

        b.startReturn().startStaticCall(context.getType(String.class), "format");
        b.doubleQuote("%s %s");
        b.string("obj.getClass().getSimpleName()");
        b.string("repr");
        b.end(2);

        b.end();

        return mFormatConstant;
    }

    private CodeExecutableElement createSetResultBoxedImpl() {
        CodeExecutableElement mSetResultBoxedImpl = new CodeExecutableElement(MOD_PROTECTED_STATIC, context.getType(void.class), "setResultBoxedImpl");
        mSetResultBoxedImpl.addParameter(new CodeVariableElement(context.getType(short[].class), "bc"));
        mSetResultBoxedImpl.addParameter(new CodeVariableElement(context.getType(int.class), "bci"));
        mSetResultBoxedImpl.addParameter(new CodeVariableElement(context.getType(int.class), "targetType"));
        mSetResultBoxedImpl.addParameter(new CodeVariableElement(context.getType(short[].class), "descriptor"));

        CodeTreeBuilder b = mSetResultBoxedImpl.createBuilder();
        b.declaration("int", "op", "bc[bci] & 0xffff");
        b.declaration("short", "todo", "descriptor[op]");

        b.startIf().string("todo > 0").end().startBlock();

        b.statement("bc[bci] = todo");

        b.end().startElseBlock();

        b.declaration("int", "offset", "(todo >> 8) & 0x7f");
        b.declaration("int", "bit", "todo & 0xff");

        b.startIf().string("targetType == 0 /* OBJECT */").end().startBlock();
        b.statement("bc[bci + offset] &= ~bit");
        b.end().startElseBlock();
        b.statement("bc[bci + offset] |= bit");
        b.end();

        b.end();

        return mSetResultBoxedImpl;
    }

    // ---------------------- builder static code -----------------

    private CodeExecutableElement createBuilderImplCtor(CodeTypeElement opNodesImpl) {
        CodeExecutableElement ctor = new CodeExecutableElement(MOD_PRIVATE, null, OPERATION_BUILDER_IMPL_NAME);
        ctor.addParameter(new CodeVariableElement(opNodesImpl.asType(), "nodes"));
        ctor.addParameter(new CodeVariableElement(context.getType(boolean.class), "isReparse"));
        ctor.addParameter(new CodeVariableElement(types.OperationConfig, "config"));

        CodeTreeBuilder b = ctor.createBuilder();

        b.statement("this.nodes = nodes");
        b.statement("this.isReparse = isReparse");
        b.statement("builtNodes = new ArrayList<>()");

        b.startIf().string("isReparse").end().startBlock();
        b.statement("builtNodes.addAll((java.util.Collection) nodes.getNodes())");
        b.end();

        b.statement("this.withSource = config.isWithSource()");
        b.statement("this.withInstrumentation = config.isWithInstrumentation()");

        b.statement("this.sourceBuilder = withSource ? new SourceInfoBuilder() : null");

        b.statement("reset()");

        return ctor;
    }

    private CodeExecutableElement createBuilderImplFinish() {
        CodeExecutableElement mFinish = new CodeExecutableElement(MOD_PRIVATE, context.getType(void.class), "finish");

        CodeTreeBuilder b = mFinish.createBuilder();

        b.startIf().string("withSource").end().startBlock();
        b.statement("nodes.setSources(sourceBuilder.buildSource())");
        b.end();

        b.startIf().string("!isReparse").end().startBlock();
        b.statement("nodes.setNodes(builtNodes.toArray(new OperationNode[0]))");
        b.end();

        return mFinish;
    }

    private CodeExecutableElement createBuilderImplReset() {
        CodeExecutableElement mReset = new CodeExecutableElement(MOD_PRIVATE, context.getType(void.class), "reset");

        CodeTreeBuilder b = mReset.createBuilder();

        b.statement("bci = 0");
        b.statement("curStack = 0");
        b.statement("maxStack = 0");
        b.statement("numLocals = 0");
        b.statement("constPool.clear()");
        b.statement("operationData = new BuilderOperationData(null, OP_BLOCK, 0, 0, false, 0)");
        b.statement("labelFills.clear()");
        b.statement("numChildNodes = 0");
        b.statement("numBranchProfiles = 0");
        b.statement("exceptionHandlers.clear()");

        for (OperationMetadataData metadata : m.getMetadatas()) {
            b.startAssign("metadata_" + metadata.getName()).string("null").end();
        }

        return mReset;
    }

    private CodeExecutableElement createBuilderImplDoBeforeEmitInstruction() {
        CodeExecutableElement mDoBeforeEmitInstruction = new CodeExecutableElement(MOD_PRIVATE, context.getType(int[].class), "doBeforeEmitInstruction");
        mDoBeforeEmitInstruction.addParameter(new CodeVariableElement(context.getType(int.class), "numPops"));
        mDoBeforeEmitInstruction.addParameter(new CodeVariableElement(context.getType(boolean.class), "pushValue"));

        CodeTreeBuilder b = mDoBeforeEmitInstruction.createBuilder();

        b.statement("int[] result = new int[numPops]");

        b.startFor().string("int i = numPops - 1; i >= 0; i--").end().startBlock();
        b.statement("curStack--");
        b.statement("int predBci = stackSourceBci[curStack]");
        b.statement("result[i] = predBci");
        b.end();

        b.startIf().string("pushValue").end().startBlock();

        b.startIf().string("curStack >= stackSourceBci.length").end().startBlock();
        b.statement("stackSourceBci = Arrays.copyOf(stackSourceBci, stackSourceBci.length * 2)");
        b.end();

        b.statement("stackSourceBci[curStack] = bci");
        b.statement("curStack++");
        b.startIf().string("curStack > maxStack").end().startBlock();
        b.statement("maxStack = curStack");
        b.end();

        b.end();

        b.startReturn().string("result").end();

        return mDoBeforeEmitInstruction;
    }
}
