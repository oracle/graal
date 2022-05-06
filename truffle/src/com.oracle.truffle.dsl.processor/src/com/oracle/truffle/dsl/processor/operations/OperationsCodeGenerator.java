package com.oracle.truffle.dsl.processor.operations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
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

    private final Set<Modifier> MOD_PUBLIC = Set.of(Modifier.PUBLIC);
    private final Set<Modifier> MOD_PUBLIC_ABSTRACT = Set.of(Modifier.PUBLIC, Modifier.ABSTRACT);
    private final Set<Modifier> MOD_PUBLIC_STATIC = Set.of(Modifier.PUBLIC, Modifier.STATIC);
    private final Set<Modifier> MOD_PRIVATE = Set.of(Modifier.PRIVATE);
    private final Set<Modifier> MOD_PRIVATE_FINAL = Set.of(Modifier.PRIVATE, Modifier.FINAL);
    private final Set<Modifier> MOD_PRIVATE_STATIC = Set.of(Modifier.PRIVATE, Modifier.STATIC);
    private final Set<Modifier> MOD_PRIVATE_STATIC_FINAL = Set.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);
    private final Set<Modifier> MOD_STATIC = Set.of(Modifier.STATIC);
    private OperationsBytecodeCodeGenerator bytecodeGenerator;

    private static final boolean FLAG_NODE_AST_PRINTING = false;
    private static final boolean ENABLE_INSTRUMENTATION = false;

    /**
     * Creates the builder class itself. This class only contains abstract methods, the builder
     * implementation class, and the <code>createBuilder</code> method.
     *
     * @return The created builder class
     */
    CodeTypeElement createBuilder(String simpleName) {
        CodeTypeElement typBuilder = GeneratorUtils.createClass(m, null, MOD_PUBLIC_ABSTRACT, simpleName, types.OperationsBuilder);

        // begin/end or emit methods
        for (Operation op : m.getOperations()) {
            List<TypeMirror> args = op.getBuilderArgumentTypes();
            ArrayList<CodeVariableElement> params = new ArrayList<>();

            for (int i = 0; i < args.size(); i++) {
                params.add(new CodeVariableElement(args.get(i), "arg" + i));
            }

            CodeVariableElement[] paramsArr = params.toArray(new CodeVariableElement[params.size()]);

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

        if (m.hasErrors()) {
            return typBuilder;
        }

        CodeTypeElement typBuilderImpl = createBuilderImpl(typBuilder);
        typBuilder.add(typBuilderImpl);
        GeneratorUtils.addSuppressWarnings(context, typBuilderImpl, "cast", "hiding", "unchecked", "rawtypes", "static-method");

        {
            CodeVariableElement parLanguage = new CodeVariableElement(m.getLanguageType(), "language");
            CodeVariableElement parParseContext = new CodeVariableElement(m.getParseContextType(), "context");
            CodeExecutableElement metParse = new CodeExecutableElement(MOD_PUBLIC_STATIC, arrayOf(types.OperationsNode), "parse");
            metParse.addParameter(parLanguage);
            metParse.addParameter(parParseContext);
            typBuilder.add(metParse);

            CodeTreeBuilder b = metParse.getBuilder();
            b.declaration(typBuilderImpl.asType(), "builder", createCreateBuilder(typBuilderImpl, parLanguage, parParseContext, false, false));
            b.startStatement().startStaticCall(m.getParseMethod());
            b.variable(parLanguage);
            b.variable(parParseContext);
            b.string("builder");
            b.end(2);
            b.startReturn().startCall("builder", "collect").end(2);
        }

        {
            CodeVariableElement parLanguage = new CodeVariableElement(m.getLanguageType(), "language");
            CodeVariableElement parParseContext = new CodeVariableElement(m.getParseContextType(), "context");
            CodeExecutableElement metParse = new CodeExecutableElement(MOD_PUBLIC_STATIC, arrayOf(types.OperationsNode), "parseWithSourceInfo");
            metParse.addParameter(parLanguage);
            metParse.addParameter(parParseContext);
            typBuilder.add(metParse);

            CodeTreeBuilder b = metParse.getBuilder();
            b.declaration(typBuilderImpl.asType(), "builder", createCreateBuilder(typBuilderImpl, parLanguage, parParseContext, true, true));
            b.startStatement().startStaticCall(m.getParseMethod());
            b.variable(parLanguage);
            b.variable(parParseContext);
            b.string("builder");
            b.end(2);
            b.startReturn().startCall("builder", "collect").end(2);
        }

        return typBuilder;
    }

    private static CodeTree createCreateBuilder(CodeTypeElement typBuilderImpl, CodeVariableElement language, CodeVariableElement parseContext, boolean withSourceInfo, boolean withInstrumentation) {
        return CodeTreeBuilder.createBuilder().startNew(typBuilderImpl.asType()) //
                        .variable(language) //
                        .variable(parseContext) //
                        .string("" + withSourceInfo) //
                        .string("" + withInstrumentation) //
                        .end().build();
    }

    private CodeTypeElement createBuilderImpl(CodeTypeElement typBuilder) {
        String simpleName = "BuilderImpl";
        CodeTypeElement typBuilderImpl = GeneratorUtils.createClass(m, null, Set.of(Modifier.PRIVATE, Modifier.STATIC), simpleName, typBuilder.asType());
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

        CodeVariableElement fldLanguage = new CodeVariableElement(MOD_PRIVATE_FINAL, m.getLanguageType(), "language");
        typBuilderImpl.add(fldLanguage);

        CodeVariableElement fldParseContext = new CodeVariableElement(MOD_PRIVATE_FINAL, m.getParseContextType(), "parseContext");
        typBuilderImpl.add(fldParseContext);

        CodeVariableElement fldKeepSourceInfo = new CodeVariableElement(MOD_PRIVATE_FINAL, context.getType(boolean.class), "keepSourceInfo");
        typBuilderImpl.add(fldKeepSourceInfo);

        CodeVariableElement fldKeepInstrumentation = new CodeVariableElement(MOD_PRIVATE_FINAL, context.getType(boolean.class), "keepInstrumentation");
        typBuilderImpl.add(fldKeepInstrumentation);

        CodeExecutableElement ctor = GeneratorUtils.createConstructorUsingFields(Set.of(), typBuilderImpl);
        typBuilderImpl.add(ctor);

        CodeVariableElement fldSourceInfoList = new CodeVariableElement(MOD_PRIVATE_FINAL, types.BuilderSourceInfo, "sourceInfoBuilder");
        typBuilderImpl.add(fldSourceInfoList);

        {
            CodeTreeBuilder b = ctor.getBuilder();

            b.startIf().variable(fldKeepSourceInfo).end();
            b.startBlock();
            b.startAssign(fldSourceInfoList).startNew(types.BuilderSourceInfo).end(2);
            b.end().startElseBlock();
            b.startAssign(fldSourceInfoList).string("null").end();
            b.end();

            b.statement("reset()");
        }

        {
            String bytesSupportClass = "com.oracle.truffle.api.operation.OperationsBytesSupport";
            // String bytesSupportClass = "com.oracle.truffle.api.memory.ByteArraySupport";
            DeclaredType byteArraySupportType = context.getDeclaredType(bytesSupportClass);
            CodeVariableElement leBytes = new CodeVariableElement(
                            MOD_PRIVATE_STATIC_FINAL,
                            byteArraySupportType, "LE_BYTES");
            leBytes.createInitBuilder().startStaticCall(byteArraySupportType, "littleEndian").end();
            typBuilderImpl.add(leBytes);
        }

        for (Operation op : m.getOperationsContext().operations) {
            CodeVariableElement fldId = new CodeVariableElement(MOD_PRIVATE_STATIC_FINAL, context.getType(int.class), "OP_" + OperationGeneratorUtils.toScreamCase(op.name));
            CodeTreeBuilder b = fldId.createInitBuilder();
            b.string("" + op.id);
            op.setIdConstantField(fldId);
            typBuilderImpl.add(fldId);
        }

        for (Instruction instr : m.getOperationsContext().instructions) {
            CodeVariableElement fldId = new CodeVariableElement(MOD_PRIVATE_STATIC_FINAL, context.getType(int.class), "INSTR_" + OperationGeneratorUtils.toScreamCase(instr.name));
            CodeTreeBuilder b = fldId.createInitBuilder();
            b.string("" + instr.id);
            instr.setOpcodeIdField(fldId);
            typBuilderImpl.add(fldId);
        }

        CodeTypeElement builderBytecodeNodeType;
        CodeTypeElement builderInstrBytecodeNodeType;
        {
            bytecodeGenerator = new OperationsBytecodeCodeGenerator(typBuilderImpl, m, false);
            builderBytecodeNodeType = bytecodeGenerator.createBuilderBytecodeNode();
            typBuilderImpl.add(builderBytecodeNodeType);
        }
        if (ENABLE_INSTRUMENTATION) {
            OperationsBytecodeCodeGenerator bcg = new OperationsBytecodeCodeGenerator(typBuilderImpl, m, true);
            builderInstrBytecodeNodeType = bcg.createBuilderBytecodeNode();
            typBuilderImpl.add(builderInstrBytecodeNodeType);
        } else {
            builderInstrBytecodeNodeType = null;
        }

        CodeVariableElement fldOperationData = new CodeVariableElement(types.BuilderOperationData, "operationData");

        CodeVariableElement fldBc = new CodeVariableElement(arrayOf(context.getType(byte.class)), "bc");
        typBuilderImpl.add(fldBc);
        {
            CodeTreeBuilder b = fldBc.createInitBuilder();
            b.string("new byte[65535]");
        }

        CodeVariableElement fldIndent = null;
        if (FLAG_NODE_AST_PRINTING) {
            fldIndent = new CodeVariableElement(MOD_PRIVATE, context.getType(int.class), "indent");
            typBuilderImpl.add(fldIndent);
        }

        CodeVariableElement fldExceptionHandlers = new CodeVariableElement(generic(context.getTypeElement(ArrayList.class), types.BuilderExceptionHandler), "exceptionHandlers");
        typBuilderImpl.add(fldExceptionHandlers);
        {
            CodeTreeBuilder b = fldExceptionHandlers.createInitBuilder();
            b.string("new ArrayList<>()");
        }

        CodeVariableElement fldLastPush = new CodeVariableElement(context.getType(int.class), "lastPush");
        typBuilderImpl.add(fldLastPush);

        CodeVariableElement fldBci = new CodeVariableElement(context.getType(int.class), "bci");
        typBuilderImpl.add(fldBci);

        CodeVariableElement fldNumChildNodes = new CodeVariableElement(context.getType(int.class), "numChildNodes");
        typBuilderImpl.add(fldNumChildNodes);

        CodeVariableElement fldNumBranchProfiles = new CodeVariableElement(context.getType(int.class), "numBranchProfiles");
        typBuilderImpl.add(fldNumBranchProfiles);

        CodeVariableElement fldBuiltNodes = new CodeVariableElement(generic(context.getTypeElement(ArrayList.class), types.OperationsNode), "builtNodes");
        typBuilderImpl.add(fldBuiltNodes);
        {
            CodeTreeBuilder b = fldBuiltNodes.createInitBuilder();
            b.string("new ArrayList<>()");
        }

        CodeVariableElement fldNodeNumber = new CodeVariableElement(context.getType(int.class), "nodeNumber");
        typBuilderImpl.add(fldNodeNumber);
        {
            CodeTreeBuilder b = fldNodeNumber.createInitBuilder();
            b.string("0");
        }

        CodeVariableElement fldConstPool = new CodeVariableElement(types.OperationsConstantPool, "constPool");
        typBuilderImpl.add(fldConstPool);
        {
            CodeTreeBuilder b = fldConstPool.createInitBuilder();
            b.startNew(types.OperationsConstantPool).end();
        }

        {
            CodeVariableElement parLanguage = new CodeVariableElement(m.getLanguageType(), "language");
            CodeVariableElement parContext = new CodeVariableElement(m.getParseContextType(), "context");
            CodeVariableElement parBuildOrder = new CodeVariableElement(context.getType(int.class), "buildOrder");
            CodeExecutableElement metParse = new CodeExecutableElement(MOD_PRIVATE_STATIC, types.OperationsNode, "reparse");
            metParse.addParameter(parLanguage);
            metParse.addParameter(parContext);
            metParse.addParameter(parBuildOrder);
            typBuilderImpl.add(metParse);

            CodeTreeBuilder b = metParse.getBuilder();
            b.declaration(typBuilderImpl.asType(), "builder", createCreateBuilder(typBuilderImpl, parLanguage, parContext, true, true));
            b.startStatement().startStaticCall(m.getParseMethod()).variable(parLanguage).variable(parContext).string("builder").end(2);
            b.startReturn().startCall("builder", "collect").end().string("[").variable(parBuildOrder).string("]").end();
        }

        BuilderVariables vars = new BuilderVariables();
        vars.bc = fldBc;
        vars.bci = fldBci;
        vars.lastChildPushCount = fldLastPush;
        vars.operationData = fldOperationData;
        vars.consts = fldConstPool;
        vars.exteptionHandlers = fldExceptionHandlers;
        vars.keepingInstrumentation = fldKeepInstrumentation;
        vars.numChildNodes = fldNumChildNodes;

        {
            CodeExecutableElement metReset = new CodeExecutableElement(
                            Set.of(Modifier.PUBLIC),
                            context.getType(void.class), "reset");
            typBuilderImpl.add(metReset);

            CodeTreeBuilder b = metReset.getBuilder();

            b.statement("super.reset()");

            b.startAssign(fldBci).string("0").end();
            b.startAssign(fldNumChildNodes).string("0").end();
            b.startAssign(fldNumBranchProfiles).string("0").end();
            if (FLAG_NODE_AST_PRINTING) {
                b.startAssign(fldIndent).string("0").end();
            }
            b.startAssign(fldOperationData).startNew(types.BuilderOperationData).string("null").string("0").string("0").string("0").string("false").end(2);
            b.startStatement().startCall(fldExceptionHandlers.getName(), "clear").end(2);
            b.startStatement().startCall(fldConstPool.getName(), "reset").end(2);

            b.startIf().variable(fldKeepSourceInfo).end();
            b.startBlock();
            {
                b.startStatement().startCall(fldSourceInfoList, "reset").end(2);
            }
            b.end();
        }

        {
            CodeExecutableElement mBuild = new CodeExecutableElement(MOD_PUBLIC, types.OperationsNode, "buildImpl");
            typBuilderImpl.add(mBuild);

            CodeTreeBuilder b = mBuild.getBuilder();

            b.statement("labelPass(bc)");

            b.startIf().string(fldOperationData.getName() + ".depth != 0").end();
            b.startBlock();
            b.startThrow().startNew(context.getType(IllegalStateException.class)).doubleQuote("Not all operations ended").end(2);
            b.end();

            b.declaration("byte[]", "bcCopy", "java.util.Arrays.copyOf(bc, bci)");
            b.declaration("Object[]", "cpCopy", "constPool.getValues()");
            b.declaration("BuilderExceptionHandler[]", "handlers", fldExceptionHandlers.getName() + ".toArray(new BuilderExceptionHandler[0])");

            b.declaration("int[][]", "sourceInfo", "null");
            b.declaration("Source[]", "sources", "null");

            b.startIf().variable(fldKeepSourceInfo).end();
            b.startBlock();

            b.startAssign("sourceInfo").startCall(fldSourceInfoList, "build").end(2);
            b.startAssign("sources").startCall(fldSourceInfoList, "buildSource").end(2);

            b.end();

            b.statement("OperationsNode result");

            b.declaration("ConditionProfile[]", "condProfiles", "new ConditionProfile[numBranchProfiles]");
            b.startFor().string("int i = 0; i < numBranchProfiles; i++").end().startBlock();
            b.statement("condProfiles[i] = ConditionProfile.createCountingProfile()");
            b.end();

            if (ENABLE_INSTRUMENTATION) {
                b.startIf().variable(fldKeepInstrumentation).end();
                b.startBlock();

                b.startAssign("result");
                b.startNew(builderInstrBytecodeNodeType.asType());
                b.variable(fldParseContext);
                b.string("sourceInfo");
                b.string("sources");
                b.variable(fldNodeNumber);
                b.string("createMaxStack()");
                b.startGroup().string("numLocals").end();
                b.string("bcCopy");
                b.string("cpCopy");
                b.startNewArray(new ArrayCodeTypeMirror(types.Node), CodeTreeBuilder.singleVariable(fldNumChildNodes)).end();
                b.string("handlers");
                b.string("condProfiles");
                b.string("getInstrumentTrees()");
                b.end(2);

                b.end().startElseBlock();
            }

            b.startAssign("result");
            b.startNew(builderBytecodeNodeType.asType());
            b.variable(fldParseContext);
            b.string("sourceInfo");
            b.string("sources");
            b.variable(fldNodeNumber);
            b.string("createMaxStack()");
            b.startGroup().string("numLocals").end();
            b.string("bcCopy");
            b.string("cpCopy");
            b.startNewArray(new ArrayCodeTypeMirror(types.Node), CodeTreeBuilder.singleVariable(fldNumChildNodes)).end();
            b.string("handlers");
            b.string("condProfiles");
            b.end(2);

            if (ENABLE_INSTRUMENTATION) {
                b.end();
            }

            b.startStatement();
            b.startCall(fldBuiltNodes, "add");
            b.string("result");
            b.end(2);

            b.startStatement().variable(fldNodeNumber).string("++").end();

            b.statement("reset()");

            b.startReturn().string("result").end();
        }

        {
            // CodeVariableElement parBci = new CodeVariableElement(context.getType(int.class),
            // "bci");
            CodeVariableElement parData = new CodeVariableElement(types.BuilderOperationData, "data");
            CodeExecutableElement mDoLeave = GeneratorUtils.overrideImplement(types.OperationsBuilder, "doLeaveOperation");
            typBuilderImpl.add(mDoLeave);

            CodeTreeBuilder b = mDoLeave.createBuilder();

            // b.startWhile().string("getCurStack() > data.stackDepth").end();
            // b.startBlock();
            //
            // b.tree(m.getOperationsContext().commonPop.createEmitCode(vars, new CodeTree[0]));
            //
            // b.end();

            b.startSwitch().string("data.operationId").end();
            b.startBlock();

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

            vars.operationData = fldOperationData;

            b.end();

        }

        {
            CodeVariableElement pSupplier = new CodeVariableElement(generic(context.getTypeElement(Supplier.class), types.Source), "supplier");
            CodeExecutableElement mBeginSource = new CodeExecutableElement(MOD_PUBLIC, context.getType(void.class), "beginSource");
            mBeginSource.addParameter(pSupplier);
            typBuilderImpl.add(mBeginSource);

            CodeTreeBuilder b = mBeginSource.getBuilder();
            b.startIf().string("!").variable(fldKeepSourceInfo).end();
            b.startBlock().returnStatement().end();

            b.startStatement().startCall("beginSource").startCall(pSupplier, "get").end(3);
        }

        {
            CodeVariableElement pSource = new CodeVariableElement(types.Source, "source");
            CodeExecutableElement mBeginSource = new CodeExecutableElement(MOD_PUBLIC, context.getType(void.class), "beginSource");
            mBeginSource.addParameter(pSource);
            typBuilderImpl.add(mBeginSource);

            CodeTreeBuilder b = mBeginSource.getBuilder();
            b.startIf().string("!").variable(fldKeepSourceInfo).end();
            b.startBlock().returnStatement().end();

            b.startStatement().startCall(fldSourceInfoList, "beginSource");
            b.variable(fldBci);
            b.variable(pSource);
            b.end(2);

        }

        {
            CodeExecutableElement mEndSource = new CodeExecutableElement(MOD_PUBLIC, context.getType(void.class), "endSource");
            typBuilderImpl.add(mEndSource);

            CodeTreeBuilder b = mEndSource.getBuilder();
            b.startIf().string("!").variable(fldKeepSourceInfo).end();
            b.startBlock().returnStatement().end();

            b.startStatement().startCall(fldSourceInfoList, "endSource");
            b.variable(fldBci);
            b.end(2);
        }

        {
            CodeExecutableElement mBeginSourceSection = GeneratorUtils.overrideImplement(types.OperationsBuilder, "beginSourceSection");
            typBuilderImpl.add(mBeginSourceSection);

            CodeTreeBuilder b = mBeginSourceSection.getBuilder();
            b.startIf().string("!").variable(fldKeepSourceInfo).end();
            b.startBlock().returnStatement().end();

            b.startStatement().startCall(fldSourceInfoList, "beginSourceSection");
            b.variable(fldBci);
            b.string("start");
            b.end(2);
        }

        {
            CodeExecutableElement mEndSourceSection = GeneratorUtils.overrideImplement(types.OperationsBuilder, "endSourceSection");
            typBuilderImpl.add(mEndSourceSection);

            CodeTreeBuilder b = mEndSourceSection.getBuilder();
            b.startIf().string("!").variable(fldKeepSourceInfo).end();
            b.startBlock().returnStatement().end();

            b.startStatement().startCall(fldSourceInfoList, "endSourceSection");
            b.variable(fldBci);
            b.string("length");
            b.end(2);

        }

        {
            CodeExecutableElement mBeforeChild = new CodeExecutableElement(context.getType(void.class), "doBeforeChild");
            CodeTreeBuilder b = mBeforeChild.getBuilder();
            GeneratorUtils.addSuppressWarnings(context, mBeforeChild, "unused");

            CodeVariableElement varChildIndex = new CodeVariableElement(context.getType(int.class), "childIndex");
            b.declaration("int", varChildIndex.getName(), "operationData.numChildren");

            vars.childIndex = varChildIndex;

            b.startSwitch().variable(fldOperationData).string(".operationId").end(2);
            b.startBlock();

            for (Operation parentOp : m.getOperations()) {

                CodeTree afterChild = parentOp.createBeforeChildCode(vars);
                if (afterChild == null)
                    continue;

                b.startCase().variable(parentOp.idConstantField).end();
                b.startBlock();

                b.tree(afterChild);

                b.statement("break");
                b.end();
            }

            b.end();

            vars.childIndex = null;

            typBuilderImpl.add(mBeforeChild);
        }
        {
            CodeExecutableElement mAfterChild = new CodeExecutableElement(context.getType(void.class), "doAfterChild");
            CodeTreeBuilder b = mAfterChild.getBuilder();
            GeneratorUtils.addSuppressWarnings(context, mAfterChild, "unused");

            CodeVariableElement varChildIndex = new CodeVariableElement(context.getType(int.class), "childIndex");
            b.declaration("int", varChildIndex.getName(), "operationData.numChildren++");

            vars.childIndex = varChildIndex;

            b.startSwitch().variable(fldOperationData).string(".operationId").end(2);
            b.startBlock();

            for (Operation parentOp : m.getOperations()) {

                CodeTree afterChild = parentOp.createAfterChildCode(vars);
                if (afterChild == null)
                    continue;

                b.startCase().variable(parentOp.idConstantField).end();
                b.startBlock();

                b.tree(afterChild);

                b.statement("break");
                b.end();
            }

            b.end();

            vars.childIndex = null;

            typBuilderImpl.add(mAfterChild);
        }

        {
            CodeVariableElement fldBoxingDescriptors = new CodeVariableElement(MOD_PRIVATE_STATIC_FINAL, arrayOf(arrayOf(context.getType(short.class))), "BOXING_DESCRIPTORS");
            typBuilderImpl.add(fldBoxingDescriptors);

            CodeTreeBuilder b = fldBoxingDescriptors.createInitBuilder();

            b.string("{").startCommaGroup();
            for (FrameKind kind : FrameKind.values()) {
                b.startGroup().newLine();
                b.lineComment("" + kind);
                if (m.getFrameKinds().contains(kind)) {
                    b.string("{").startCommaGroup();
                    b.string("-1");
                    for (Instruction instr : m.getInstructions()) {
                        String value;
                        switch (instr.boxingEliminationBehaviour()) {
                            case DO_NOTHING:
                                value = "0";
                                break;
                            case REPLACE:
                                value = instr.boxingEliminationReplacement(kind).getName();
                                break;
                            case SET_BIT:
                                value = "(short) (0x8000 | (" + instr.boxingEliminationBitOffset() + " << 8) | 0x" + Integer.toHexString(instr.boxingEliminationBitMask()) + ")";
                                break;
                            default:
                                throw new UnsupportedOperationException("unknown boxing behaviour: " + instr.boxingEliminationBehaviour());
                        }
                        b.string(value);

                    }
                    b.end().string("}").end();
                } else {
                    b.string("null").end();
                }
            }
            b.end().string("}");
        }

        {
            CodeExecutableElement mDoSetResultUnboxed = new CodeExecutableElement(MOD_PRIVATE_STATIC, context.getType(void.class), "doSetResultBoxed");
            builderBytecodeNodeType.add(mDoSetResultUnboxed);

            CodeVariableElement varBc = new CodeVariableElement(arrayOf(context.getType(byte.class)), "bc");
            mDoSetResultUnboxed.addParameter(varBc);

            CodeVariableElement varStartBci = new CodeVariableElement(context.getType(int.class), "startBci");
            mDoSetResultUnboxed.addParameter(varStartBci);

            CodeVariableElement varBciOffset = new CodeVariableElement(context.getType(int.class), "bciOffset");
            mDoSetResultUnboxed.addParameter(varBciOffset);

            CodeVariableElement varTargetType = new CodeVariableElement(context.getType(int.class), "targetType");
            mDoSetResultUnboxed.addParameter(varTargetType);

            CodeTreeBuilder b = mDoSetResultUnboxed.createBuilder();

            b.startIf().variable(varBciOffset).string(" != 0").end().startBlock();
            {
                b.startStatement().startCall("setResultBoxedImpl");
                b.variable(varBc);
                b.string("startBci - bciOffset");
                b.variable(varTargetType);
                b.startGroup().string("BOXING_DESCRIPTORS[").variable(varTargetType).string("]").end();
                b.end(2);
            }
            b.end();
        }

        for (Operation op : m.getOperations()) {
            List<TypeMirror> args = op.getBuilderArgumentTypes();
            CodeVariableElement[] params = new CodeVariableElement[args.size()];

            for (int i = 0; i < params.length; i++) {
                params[i] = new CodeVariableElement(args.get(i), "arg" + i);
            }

            if (op.children != 0) {
                CodeExecutableElement metBegin = GeneratorUtils.overrideImplement(typBuilder, "begin" + op.name);
                typBuilderImpl.add(metBegin);
                GeneratorUtils.addSuppressWarnings(context, metBegin, "unused");

                CodeExecutableElement metEnd = GeneratorUtils.overrideImplement(typBuilder, "end" + op.name);
                typBuilderImpl.add(metEnd);
                GeneratorUtils.addSuppressWarnings(context, metEnd, "unused");

                {
                    // doBeforeChild();

                    // operationData = new ...(operationData, ID, <x>, args...);

                    // << begin >>

                    CodeTreeBuilder b = metBegin.getBuilder();

                    if (op instanceof InstrumentTag) {
                        // this needs to be placed here, at the very start
                        // of the begin/end methods
                        b.startIf();
                        if (ENABLE_INSTRUMENTATION) {
                            b.string("!").variable(vars.keepingInstrumentation);
                        } else {
                            b.string("true");
                        }
                        b.end();
                        b.startBlock();
                        b.returnStatement();
                        b.end();
                    }

                    if (FLAG_NODE_AST_PRINTING) {
                        b.statement("System.out.print(\"\\n\" + \" \".repeat(indent) + \"(" + op.name + "\")");
                        b.statement("indent++");
                    }

                    b.statement("doBeforeChild()");

                    b.startAssign(fldOperationData).startNew(types.BuilderOperationData);

                    b.variable(fldOperationData);
                    b.variable(op.idConstantField);
                    b.string("getCurStack()");
                    b.string("" + op.getNumAuxValues());
                    b.string("" + op.hasLeaveCode());

                    for (VariableElement el : metBegin.getParameters()) {
                        b.variable(el);
                    }

                    b.end(2);

                    b.tree(op.createBeginCode(vars));
                }

                {
                    // if (operationData.id != ID) throw;
                    // << end >>

                    // operationData = operationData.parent;

                    // doAfterChild();
                    CodeTreeBuilder b = metEnd.getBuilder();

                    if (op instanceof InstrumentTag) {
                        // this needs to be placed here, at the very start
                        // of the begin/end methods
                        b.startIf();
                        if (ENABLE_INSTRUMENTATION) {
                            b.string("!").variable(vars.keepingInstrumentation);
                        } else {
                            b.string("true");
                        }
                        b.end();
                        b.startBlock();
                        b.returnStatement();
                        b.end();
                    }

                    if (FLAG_NODE_AST_PRINTING) {
                        b.statement("System.out.print(\")\")");
                        b.statement("indent--");
                    }

                    b.startIf().string("operationData.operationId != ").variable(op.idConstantField).end();
                    b.startBlock();
                    b.startThrow().startNew(context.getType(IllegalStateException.class))//
                                    .startGroup()//
                                    .doubleQuote("Mismatched begin/end, expected ")//
                                    .string(" + operationData.operationId").end(3);
                    b.end();

                    vars.numChildren = new CodeVariableElement(context.getType(int.class), "numChildren");
                    b.declaration("int", "numChildren", "operationData.numChildren");

                    if (!op.isVariableChildren()) {
                        b.startIf().string("numChildren != " + op.children).end();
                        b.startBlock();
                        b.startThrow().startNew(context.getType(IllegalStateException.class))//
                                        .startGroup()//
                                        .doubleQuote(op.name + " expected " + op.children + " children, got ")//
                                        .string(" + numChildren")//
                                        .end(3);
                        b.end();
                    } else {
                        b.startIf().string("numChildren < " + op.minimumChildren()).end();
                        b.startBlock();
                        b.startThrow().startNew(context.getType(IllegalStateException.class))//
                                        .startGroup()//
                                        .doubleQuote(op.name + " expected at least " + op.minimumChildren() + " children, got ")//
                                        .string(" + numChildren")//
                                        .end(3);
                        b.end();
                    }

                    b.tree(op.createEndCode(vars));

                    CodeTree lastPush = op.createPushCountCode(vars);
                    if (lastPush != null) {
                        b.startAssign(fldLastPush).tree(lastPush).end();
                    }

                    b.startAssign(fldOperationData).variable(fldOperationData).string(".parent").end();

                    b.statement("doAfterChild()");

                    vars.numChildren = null;

                }
            } else {
                CodeExecutableElement metEmit = GeneratorUtils.overrideImplement(typBuilder, "emit" + op.name);
                {
                    CodeTreeBuilder b = metEmit.getBuilder();

                    if (FLAG_NODE_AST_PRINTING) {
                        b.statement("System.out.print(\"\\n\" + \" \".repeat(indent) + \"(" + op.name + ")\")");
                    }

                    b.statement("doBeforeChild()");

                    if (op.name.equals("Label")) {
                        // for labels we need to use local to not conflict with operation layering
                        // requirements. we still create the variable since some other code expects
                        // it to exist (it will shadow the actual field)
                        b.string(types.BuilderOperationData.asElement().getSimpleName() + " ");
                    }
                    b.startAssign(fldOperationData);
                    b.startNew(types.BuilderOperationData);

                    b.field("this", fldOperationData);
                    b.variable(op.idConstantField);
                    b.string("getCurStack()");
                    b.string("" + op.getNumAuxValues());
                    b.string("false");

                    for (VariableElement v : metEmit.getParameters()) {
                        b.variable(v);
                    }
                    b.end(2);

                    b.tree(op.createBeginCode(vars));
                    b.tree(op.createEndCode(vars));
                    b.startAssign(fldLastPush).tree(op.createPushCountCode(vars)).end();

                    if (!op.name.equals("Label")) {
                        b.startAssign(fldOperationData).variable(fldOperationData).string(".parent").end();
                    }

                    b.statement("doAfterChild()");

                }
                typBuilderImpl.add(metEmit);
            }
        }

        return typBuilderImpl;

    }

    private static TypeMirror generic(TypeElement el, TypeMirror... params) {
        return new DeclaredCodeTypeMirror(el, Arrays.asList(params));
    }

    private static TypeMirror arrayOf(TypeMirror el) {
        return new ArrayCodeTypeMirror(el);
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
