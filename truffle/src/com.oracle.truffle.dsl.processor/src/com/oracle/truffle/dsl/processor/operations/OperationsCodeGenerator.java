package com.oracle.truffle.dsl.processor.operations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.function.Supplier;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.AnnotationProcessor;
import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.generator.CodeTypeElementFactory;
import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationValue;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.ArrayCodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.DeclaredCodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.operations.Instruction.ExecuteVariables;
import com.oracle.truffle.dsl.processor.operations.Operation.BuilderVariables;

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

    private static final boolean FLAG_NODE_AST_PRINTING = false;

    /**
     * Creates the builder class itself. This class only contains abstract methods, the builder
     * implementation class, and the <code>createBuilder</code> method.
     *
     * @return The created builder class
     */
    CodeTypeElement createBuilder(String simpleName) {
        CodeTypeElement typBuilder = GeneratorUtils.createClass(m, null, MOD_PUBLIC_ABSTRACT, simpleName, types.OperationsBuilder);

        CodeExecutableElement metCreateLabel = new CodeExecutableElement(MOD_PUBLIC_ABSTRACT, types.OperationLabel, "createLabel");
        typBuilder.add(metCreateLabel);

        // begin/end or emit methods
        for (Operation op : m.getOperations()) {
            List<Argument> args = op.getArguments();
            ArrayList<CodeVariableElement> params = new ArrayList<>();

            for (int i = 0; i < args.size(); i++) {
                if (args.get(i).isImplicit()) {
                    continue;
                }

                params.add(new CodeVariableElement(args.get(i).toBuilderArgumentType(), "arg" + i));
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

        {
            CodeVariableElement parLanguage = new CodeVariableElement(m.getLanguageType(), "language");
            CodeVariableElement parParseContext = new CodeVariableElement(m.getParseContextType(), "context");
            CodeExecutableElement metParse = new CodeExecutableElement(MOD_PUBLIC_STATIC, arrayOf(types.OperationsNode), "parse");
            metParse.addParameter(parLanguage);
            metParse.addParameter(parParseContext);
            typBuilder.add(metParse);

            CodeTreeBuilder b = metParse.getBuilder();
            b.declaration(typBuilderImpl.asType(), "builder", createCreateBuilder(typBuilderImpl, parLanguage, parParseContext, false));
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
            b.declaration(typBuilderImpl.asType(), "builder", createCreateBuilder(typBuilderImpl, parLanguage, parParseContext, true));
            b.startStatement().startStaticCall(m.getParseMethod());
            b.variable(parLanguage);
            b.variable(parParseContext);
            b.string("builder");
            b.end(2);
            b.startReturn().startCall("builder", "collect").end(2);
        }

        return typBuilder;
    }

    private static CodeTree createCreateBuilder(CodeTypeElement typBuilderImpl, CodeVariableElement language, CodeVariableElement parseContext, boolean withSourceInfo) {
        return CodeTreeBuilder.createBuilder().startNew(typBuilderImpl.asType()).variable(language).variable(parseContext).string("" + withSourceInfo).end().build();
    }

    private CodeTypeElement createBuilderImpl(CodeTypeElement typBuilder) {
        String simpleName = m.getTemplateType().getSimpleName() + "BuilderImpl";
        CodeTypeElement typBuilderImpl = GeneratorUtils.createClass(m, null, Set.of(Modifier.PRIVATE, Modifier.STATIC), simpleName, typBuilder.asType());
        typBuilderImpl.setEnclosingElement(typBuilder);

        CodeVariableElement fldLanguage = new CodeVariableElement(MOD_PRIVATE_FINAL, m.getLanguageType(), "language");
        typBuilderImpl.add(fldLanguage);

        CodeVariableElement fldParseContext = new CodeVariableElement(MOD_PRIVATE_FINAL, m.getParseContextType(), "parseContext");
        typBuilderImpl.add(fldParseContext);

        CodeVariableElement fldKeepSourceInfo = new CodeVariableElement(MOD_PRIVATE_FINAL, context.getType(boolean.class), "keepSourceInfo");
        typBuilderImpl.add(fldKeepSourceInfo);

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

        DeclaredType byteArraySupportType = context.getDeclaredType("com.oracle.truffle.api.memory.ByteArraySupport");
        CodeVariableElement leBytes = new CodeVariableElement(
                        MOD_PRIVATE_STATIC_FINAL,
                        byteArraySupportType, "LE_BYTES");
        leBytes.createInitBuilder().startStaticCall(byteArraySupportType, "littleEndian").end();
        typBuilderImpl.add(leBytes);

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

        CodeTypeElement builderLabelImplType = createBuilderLabelImpl(simpleName + "Label");
        typBuilderImpl.add(builderLabelImplType);

        CodeTypeElement builderBytecodeNodeType = createBuilderBytecodeNode(typBuilderImpl, simpleName + "BytecodeNode");
        typBuilderImpl.add(builderBytecodeNodeType);

        CodeVariableElement fldChildIndexStack = createStackField("childIndex", context.getType(Integer.class));
        typBuilderImpl.add(fldChildIndexStack);

        CodeVariableElement fldArgumentStack = createStackField("argument", new ArrayCodeTypeMirror(context.getType(Object.class)));
        typBuilderImpl.add(fldArgumentStack);

        CodeVariableElement fldTypeStack = createStackField("type", context.getType(Integer.class));
        typBuilderImpl.add(fldTypeStack);

        CodeVariableElement fldUtilityStack = createStackField("utility", context.getType(Object.class));
        typBuilderImpl.add(fldUtilityStack);

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

        CodeVariableElement fldMaxLocal = new CodeVariableElement(context.getType(int.class), "maxLocal");
        typBuilderImpl.add(fldMaxLocal);

        CodeVariableElement fldMaxStack = new CodeVariableElement(context.getType(int.class), "maxStack");
        typBuilderImpl.add(fldMaxStack);

        CodeVariableElement fldCurStack = new CodeVariableElement(context.getType(int.class), "curStack");
        typBuilderImpl.add(fldCurStack);

        CodeVariableElement fldBci = new CodeVariableElement(context.getType(int.class), "bci");
        typBuilderImpl.add(fldBci);

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
            b.declaration(typBuilderImpl.asType(), "builder", createCreateBuilder(typBuilderImpl, parLanguage, parContext, true));
            b.startStatement().startStaticCall(m.getParseMethod()).variable(parLanguage).variable(parContext).string("builder").end(2);
            b.startReturn().startCall("builder", "collect").end().string("[").variable(parBuildOrder).string("]").end();
        }

        BuilderVariables vars = new BuilderVariables();
        vars.bc = fldBc;
        vars.bci = fldBci;
        vars.lastChildPushCount = fldLastPush;
        vars.stackUtility = fldUtilityStack;
        vars.consts = fldConstPool;
        vars.maxStack = fldMaxStack;
        vars.curStack = fldCurStack;
        vars.maxLocal = fldMaxLocal;
        vars.exteptionHandlers = fldExceptionHandlers;

        {
            CodeExecutableElement metReset = new CodeExecutableElement(
                            Set.of(Modifier.PUBLIC),
                            context.getType(void.class), "reset");
            typBuilderImpl.add(metReset);

            CodeTreeBuilder b = metReset.getBuilder();
            b.startAssign(fldBci).string("0").end();
            b.startAssign(fldCurStack).string("0").end();
            b.startAssign(fldMaxStack).string("0").end();
            b.startAssign(fldMaxLocal).string("-1").end();
            if (FLAG_NODE_AST_PRINTING) {
                b.startAssign(fldIndent).string("0").end();
            }
            b.startStatement().startCall(fldChildIndexStack.getName(), "clear").end(2);
            b.startStatement().startCall(fldChildIndexStack.getName(), "add").string("0").end(2);
            b.startStatement().startCall(fldArgumentStack.getName(), "clear").end(2);
            b.startStatement().startCall(fldTypeStack.getName(), "clear").end(2);
            b.startStatement().startCall(fldTypeStack.getName(), "add").string("0").end(2);
            b.startStatement().startCall(fldExceptionHandlers.getName(), "clear").end(2);
            b.startStatement().startCall(fldConstPool.getName(), "reset").end(2);
            b.startAssign("nodeName").string("null").end();
            b.startAssign("isInternal").string("false").end();

            b.startIf().variable(fldKeepSourceInfo).end();
            b.startBlock();
            {
                b.startStatement().startCall(fldSourceInfoList, "reset").end(2);
            }
            b.end();
        }

        {
            CodeExecutableElement mBuild = new CodeExecutableElement(MOD_PUBLIC, types.OperationsNode, "build");
            typBuilderImpl.add(mBuild);

            CodeTreeBuilder b = mBuild.getBuilder();

            b.startIf().string(fldChildIndexStack.getName() + ".size() != 1").end();
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

            b.startAssign("OperationsNode result");
            b.startNew(builderBytecodeNodeType.asType());
            b.variable(fldLanguage);
            b.variable(fldParseContext);
            b.string("nodeName");
            b.string("isInternal");
            b.string("sourceInfo");
            b.string("sources");
            b.variable(fldNodeNumber);
            b.variable(fldMaxStack);
            b.startGroup().variable(fldMaxLocal).string(" + 1").end();
            b.string("bcCopy");
            b.string("cpCopy");
            b.string("handlers");

            b.end(2);

            b.startStatement();
            b.startCall(fldBuiltNodes, "add");
            b.string("result");
            b.end(2);

            b.startStatement().variable(fldNodeNumber).string("++").end();

            b.statement("reset()");

            b.startReturn().string("result").end();
        }

        {
            CodeExecutableElement mCollect = GeneratorUtils.overrideImplement(types.OperationsBuilder, "collect");
            typBuilderImpl.add(mCollect);

            CodeTreeBuilder b = mCollect.getBuilder();

            b.startReturn();
            b.startCall(fldBuiltNodes, "toArray");
            b.string("new OperationsNode[builtNodes.size()]");
            b.end(2);
        }

        {
            CodeExecutableElement mCreateLabel = GeneratorUtils.overrideImplement(typBuilder, "createLabel");
            typBuilderImpl.add(mCreateLabel);

            CodeTreeBuilder b = mCreateLabel.getBuilder();
            b.startReturn();
            b.startNew(builderLabelImplType.asType());
            b.end(2);
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
            b.declaration("int", varChildIndex.getName(), "childIndexStack.peek()");

            vars.childIndex = varChildIndex;

            b.startSwitch().startCall(fldTypeStack, "peek").end(2);
            b.startBlock();

            for (Operation parentOp : m.getOperations()) {

                CodeTree afterChild = parentOp.createBeforeChildCode(vars);
                if (afterChild == null)
                    continue;

                b.startCase().variable(parentOp.idConstantField).end();
                b.startBlock();

// b.statement("System.out.println(\"\\n## beforechild " + parentOp.name + " \" + childIndex)");

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
            b.declaration("int", varChildIndex.getName(), "childIndexStack.pop()");
            b.startStatement().startCall(fldChildIndexStack, "push").string(varChildIndex.getName() + " + 1").end(2);

            vars.childIndex = varChildIndex;

            b.startSwitch().startCall(fldTypeStack, "peek").end(2);
            b.startBlock();

            for (Operation parentOp : m.getOperations()) {

                CodeTree afterChild = parentOp.createAfterChildCode(vars);
                if (afterChild == null)
                    continue;

                b.startCase().variable(parentOp.idConstantField).end();
                b.startBlock();

// b.statement("System.out.println(\"\\n## afterchild " + parentOp.name + " \" + childIndex)");

                b.tree(afterChild);

                b.statement("break");
                b.end();
            }

            b.end();

            vars.childIndex = null;

            typBuilderImpl.add(mAfterChild);
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

                    // typeStack.push(ID);
                    // childIndexStack.push(0);

                    // Object[] args = new Object[...];
                    // args[x] = arg_x;...
                    // argumentStack.push(args);

                    // << begin >>

                    CodeTreeBuilder b = metBegin.getBuilder();

                    if (FLAG_NODE_AST_PRINTING) {
                        b.statement("System.out.print(\"\\n\" + \" \".repeat(indent) + \"(" + op.name + "\")");
                        b.statement("indent++");
                    }

                    b.statement("doBeforeChild()");

                    b.startStatement().startCall(fldTypeStack, "push").variable(op.idConstantField).end(2);
                    b.startStatement().startCall(fldChildIndexStack, "push").string("0").end(2);

                    vars.arguments = metBegin.getParameters().toArray(new CodeVariableElement[0]);

                    if (vars.arguments.length > 0) {
                        b.declaration("Object[]", "args", "new Object[" + vars.arguments.length + "]");
                        for (int i = 0; i < vars.arguments.length; i++) {
                            b.statement("args[" + i + "] = " + vars.arguments[i].getName());
                        }

                        b.startStatement().startCall(fldArgumentStack, "push").string("args").end(2);
                    }

                    b.tree(op.createBeginCode(vars));

                    vars.arguments = null;

                }

                {
                    // assert typeStack.pop() == ID;
                    // int numChildren = childIndexStack.pop();
                    // Object[] args = argumentsStack.pop();
                    // Object arg_x = (...) args[x]; ...
                    // << end >>

                    // doAfterChild();
                    CodeTreeBuilder b = metEnd.getBuilder();

                    if (FLAG_NODE_AST_PRINTING) {
                        b.statement("System.out.print(\")\")");
                        b.statement("indent--");
                    }

                    b.declaration("int", "typePop", CodeTreeBuilder.createBuilder().startCall(fldTypeStack, "pop").end().build());

                    b.startIf().string("typePop != ").variable(op.idConstantField).end();
                    b.startBlock();
                    b.startThrow().startNew(context.getType(IllegalStateException.class))//
                                    .startGroup()//
                                    .doubleQuote("Mismatched begin/end, expected ")//
                                    .string(" + typePop").end(3);
                    b.end();

                    vars.numChildren = new CodeVariableElement(context.getType(int.class), "numChildren");
                    b.declaration("int", "numChildren", "childIndexStack.pop()");

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

                    List<Argument> argInfo = op.getArguments();

                    if (argInfo.size() > 0) {
                        if (metBegin.getParameters().size() > 0) {
                            // some non-implicit parameters
                            b.declaration("Object[]", "args", fldArgumentStack.getName() + ".pop()");
                        }

                        CodeVariableElement[] varArgs = new CodeVariableElement[argInfo.size()];
                        for (int i = 0; i < argInfo.size(); i++) {
                            if (argInfo.get(i).isImplicit())
                                continue;

                            // TODO: this does not work with mixing implicit and real args

                            varArgs[i] = new CodeVariableElement(argInfo.get(i).toBuilderArgumentType(), "arg_" + i);

                            CodeTreeBuilder b2 = CodeTreeBuilder.createBuilder();
                            b2.maybeCast(context.getType(Object.class), argInfo.get(i).toBuilderArgumentType());
                            b2.string("args[" + i + "]");

                            b.declaration(argInfo.get(i).toBuilderArgumentType(), "arg_" + i, b2.build());
                        }

                        vars.arguments = varArgs;
                    }

                    b.tree(op.createEndCode(vars));
                    CodeTree lastPush = op.createPushCountCode(vars);
                    if (lastPush != null) {
                        b.startAssign(fldLastPush).tree(lastPush).end();
                    }

                    b.statement("doAfterChild()");

                    vars.arguments = null;
                    vars.numChildren = null;

                }
            } else {
                CodeExecutableElement metEmit = GeneratorUtils.overrideImplement(typBuilder, "emit" + op.name);
                {
                    CodeTreeBuilder b = metEmit.getBuilder();

                    if (FLAG_NODE_AST_PRINTING) {
                        b.statement("System.out.print(\"\\n\" + \" \".repeat(indent) + \"(" + op.name + ")\")");
                    }

                    vars.arguments = metEmit.getParameters().toArray(new CodeVariableElement[0]);

                    b.statement("doBeforeChild()");
                    b.tree(op.createBeginCode(vars));
                    b.tree(op.createEndCode(vars));
                    b.startAssign(fldLastPush).tree(op.createPushCountCode(vars)).end();
                    b.statement("doAfterChild()");

                    vars.arguments = null;
                }
                typBuilderImpl.add(metEmit);
            }
        }

        return typBuilderImpl;

    }

    private CodeTypeElement createBuilderLabelImpl(String simpleName) {
        return GeneratorUtils.createClass(m, null, Set.of(Modifier.PRIVATE, Modifier.STATIC), simpleName, types.BuilderOperationLabel);
    }

    /**
     * Create the BytecodeNode type. This type contains the bytecode interpreter, and is the
     * executable Truffle node.
     */
    private CodeTypeElement createBuilderBytecodeNode(CodeTypeElement typBuilderImpl, String simpleName) {
        CodeTypeElement builderBytecodeNodeType = GeneratorUtils.createClass(m, null, MOD_PRIVATE_STATIC_FINAL, simpleName, types.OperationsNode);

        CodeVariableElement fldBc = new CodeVariableElement(MOD_PRIVATE_FINAL, arrayOf(context.getType(byte.class)), "bc");
        GeneratorUtils.addCompilationFinalAnnotation(fldBc, 1);
        builderBytecodeNodeType.add(fldBc);

        CodeVariableElement fldConsts = new CodeVariableElement(MOD_PRIVATE_FINAL, arrayOf(context.getType(Object.class)), "consts");
        GeneratorUtils.addCompilationFinalAnnotation(fldConsts, 1);
        builderBytecodeNodeType.add(fldConsts);

        CodeVariableElement fldHandlers = new CodeVariableElement(MOD_PRIVATE_FINAL, arrayOf(types.BuilderExceptionHandler), "handlers");
        GeneratorUtils.addCompilationFinalAnnotation(fldHandlers, 1);
        builderBytecodeNodeType.add(fldHandlers);

        CodeExecutableElement ctor = GeneratorUtils.createConstructorUsingFields(Set.of(), builderBytecodeNodeType);
        builderBytecodeNodeType.add(ctor);

        CodeVariableElement fldTracer = null;
        CodeVariableElement fldHitCount = null;
        if (m.isTracing()) {
            fldHitCount = new CodeVariableElement(MOD_PRIVATE_FINAL, arrayOf(context.getType(int.class)), "hitCount");
            builderBytecodeNodeType.add(fldHitCount);

            fldTracer = new CodeVariableElement(types.ExecutionTracer, "tracer");
            builderBytecodeNodeType.add(fldTracer);
        }

        {
            CodeTreeBuilder b = ctor.getBuilder();

            if (m.isTracing()) {
                b.startAssign(fldHitCount).startNewArray(
                                (ArrayType) fldHitCount.getType(),
                                CodeTreeBuilder.createBuilder().variable(fldBc).string(".length").build());
                b.end(2);

                b.startAssign(fldTracer).startStaticCall(types.ExecutionTracer, "get").end(2);
            }
        }

        ExecuteVariables vars = new ExecuteVariables();
        vars.bytecodeNodeType = builderBytecodeNodeType;
        vars.bc = fldBc;
        vars.consts = fldConsts;
        vars.maxStack = new CodeVariableElement(context.getType(int.class), "maxStack");
        vars.handlers = fldHandlers;
        vars.tracer = fldTracer;

        {
            CodeExecutableElement mExecute = new CodeExecutableElement(Set.of(Modifier.PUBLIC), context.getType(Object.class), "execute", new CodeVariableElement(types.VirtualFrame, "frame"));
            builderBytecodeNodeType.add(mExecute);

            CodeTreeBuilder builder = mExecute.getBuilder();
            builder.startReturn();
            builder.startCall("continueAt");

            builder.string("frame");
            builder.string("null");

            builder.end(2);

        }

        {
            CodeVariableElement argFrame = new CodeVariableElement(types.VirtualFrame, "frame");
            CodeVariableElement argStartIndex = new CodeVariableElement(types.OperationLabel, "startIndex");
            CodeExecutableElement mContinueAt = new CodeExecutableElement(
                            Set.of(Modifier.PUBLIC), context.getType(Object.class), "continueAt",
                            argFrame, argStartIndex);
            builderBytecodeNodeType.add(mContinueAt);

            {
                CodeAnnotationMirror annExplodeLoop = new CodeAnnotationMirror(types.ExplodeLoop);
                mContinueAt.addAnnotationMirror(annExplodeLoop);
                annExplodeLoop.setElementValue("kind", new CodeAnnotationValue(new CodeVariableElement(
                                context.getDeclaredType("com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind"), "MERGE_EXPLODE")));
            }

            CodeTreeBuilder b = mContinueAt.getBuilder();

            CodeVariableElement varSp = new CodeVariableElement(context.getType(int.class), "sp");
            CodeVariableElement varBci = new CodeVariableElement(context.getType(int.class), "bci");
            CodeVariableElement varCurOpcode = new CodeVariableElement(context.getType(byte.class), "curOpcode");

            b.declaration("int", varSp.getName(), "maxLocals + VALUES_OFFSET");
            b.declaration("int", varBci.getName(), "0");

            if (m.isTracing()) {
                b.startStatement().startCall(fldTracer, "startFunction").string("this").end(2);
            }

            CodeVariableElement varReturnValue = new CodeVariableElement(context.getType(Object.class), "returnValue");
            b.statement("Object " + varReturnValue.getName());

            b.string("loop: ");
            b.startWhile().string("true").end();
            b.startBlock();
            CodeVariableElement varNextBci = new CodeVariableElement(context.getType(int.class), "nextBci");
            b.statement("int nextBci");

            vars.bci = varBci;
            vars.nextBci = varNextBci;
            vars.frame = argFrame;
            vars.sp = varSp;
            vars.returnValue = varReturnValue;

            b.declaration("byte", varCurOpcode.getName(), CodeTreeBuilder.singleString("bc[bci]"));

            b.startTryBlock();

            b.tree(GeneratorUtils.createPartialEvaluationConstant(varBci));
            b.tree(GeneratorUtils.createPartialEvaluationConstant(varSp));
            b.tree(GeneratorUtils.createPartialEvaluationConstant(varCurOpcode));

            if (m.isTracing()) {
                b.startStatement().variable(fldHitCount).string("[bci]++").end();
            }

            b.startIf().variable(varSp).string(" < maxLocals + VALUES_OFFSET").end();
            b.startBlock();
            b.tree(GeneratorUtils.createShouldNotReachHere("stack underflow"));
            b.end();

            b.startSwitch().string("curOpcode").end();
            b.startBlock();

            for (Instruction op : m.getInstructions()) {
                b.startCase().variable(op.opcodeIdField).end();
                b.startBlock();

                if (m.isTracing()) {
                    b.startStatement().startCall(fldTracer, "traceInstruction");
                    b.variable(varBci);
                    b.variable(op.opcodeIdField);

                    int ofs = 1;
                    for (Argument arg : op.arguments) {
                        b.tree(arg.createReadCode(vars, ofs));
                        ofs += arg.length;
                    }

                    b.end(2);
                }

                b.tree(op.createExecuteCode(vars));
                b.tree(op.createExecuteEpilogue(vars));

                b.end();
            }

            b.caseDefault().startCaseBlock().tree(GeneratorUtils.createShouldNotReachHere("unknown opcode encountered")).end();

            b.end(); // switch block

            b.end().startCatchBlock(context.getDeclaredType("com.oracle.truffle.api.exception.AbstractTruffleException"), "ex");

            if (m.isTracing()) {
                b.startStatement().startCall(fldTracer, "traceException");
                b.string("ex");
                b.end(2);

            }

            b.startFor().string("int handlerIndex = 0; handlerIndex < " + vars.handlers.getName() + ".length; handlerIndex++").end();
            b.startBlock();

            b.declaration(types.BuilderExceptionHandler, "handler", vars.handlers.getName() + "[handlerIndex]");
            b.startIf().string("handler.startBci > bci || handler.endBci <= bci").end();
            b.statement("continue");

            b.startAssign(varSp).string("handler.startStack + VALUES_OFFSET").end();
            // TODO check exception type (?)

            b.tree(OperationGeneratorUtils.createWriteLocal(vars,
                            CodeTreeBuilder.singleString("handler.exceptionIndex"),
                            CodeTreeBuilder.singleString("ex")));

            b.statement("bci = handler.handlerBci");
            b.statement("continue loop");

            b.end(); // for (handlerIndex ...)

            b.startThrow().string("ex").end();

            b.end(); // catch block

            b.statement("bci = nextBci");
            b.end(); // while block

            if (m.isTracing()) {
                b.startStatement().startCall(fldTracer, "endFunction").end(2);
            }

            b.startReturn().string("returnValue").end();

            vars.bci = null;
            vars.nextBci = null;
            vars.frame = null;
            vars.sp = null;
            vars.returnValue = null;

        }

        {
            CodeExecutableElement mDump = new CodeExecutableElement(Set.of(Modifier.PUBLIC), context.getType(String.class), "dump");
            builderBytecodeNodeType.add(mDump);

            CodeTreeBuilder b = mDump.getBuilder();

            b.declaration("int", "bci", "0");
            b.declaration("StringBuilder", "sb", "new StringBuilder()");

            vars.bci = new CodeVariableElement(context.getType(int.class), "bci");

            b.startWhile().string("bci < bc.length").end();
            b.startBlock(); // while block

            if (m.isTracing()) {
                b.statement("sb.append(String.format(\" [ %3d ]\", hitCount[bci]))");
            }

            b.statement("sb.append(String.format(\" %04x \", bci))");

            b.startSwitch().string("bc[bci]").end();
            b.startBlock();

            for (Instruction op : m.getInstructions()) {
                b.startCase().variable(op.opcodeIdField).end();
                b.startBlock();

                b.statement("sb.append(\"" + op.name + " \")");

                int ofs = 1;
                for (int i = 0; i < op.arguments.length; i++) {
                    b.tree(op.arguments[i].getDumpCode(vars, CodeTreeBuilder.singleString("bci + " + ofs)));
                    ofs += op.arguments[i].length;
                }

                b.statement("bci += " + op.length());
                b.statement("break");

                b.end();
            }

            b.caseDefault().startCaseBlock();
            b.statement("sb.append(String.format(\"unknown 0x%02x\", bc[bci++]))");
            b.statement("break");
            b.end(); // default case block
            b.end(); // switch block

            b.statement("sb.append(\"\\n\")");

            b.end(); // while block

            b.startFor().string("int i = 0; i < ").variable(fldHandlers).string(".length; i++").end();
            b.startBlock();

            b.startStatement().string("sb.append(").variable(fldHandlers).string("[i] + \"\\n\")").end();

            b.end();

            b.startIf().string("sourceInfo != null").end();
            b.startBlock();
            {
                b.statement("sb.append(\"Source info:\\n\")");
                b.startFor().string("int i = 0; i < sourceInfo[0].length; i++").end();
                b.startBlock();

                b.statement("sb.append(String.format(\"  bci=%04x, offset=%d, length=%d\\n\", sourceInfo[0][i], sourceInfo[1][i], sourceInfo[2][i]))");

                b.end();
            }
            b.end();

            b.startReturn().string("sb.toString()").end();

            vars.bci = null;

        }

        if (m.isTracing()) {
            CodeExecutableElement mGetTrace = GeneratorUtils.override(types.OperationsNode, "getNodeTrace");
            builderBytecodeNodeType.add(mGetTrace);

            CodeTreeBuilder b = mGetTrace.getBuilder();

            b.declaration("int", "bci", "0");
            b.declaration("ArrayList<InstructionTrace>", "insts", "new ArrayList<>()");

            vars.bci = new CodeVariableElement(context.getType(int.class), "bci");

            b.startWhile().string("bci < bc.length").end();
            b.startBlock(); // while block

            b.startSwitch().string("bc[bci]").end();
            b.startBlock();

            for (Instruction op : m.getInstructions()) {
                b.startCase().variable(op.opcodeIdField).end();
                b.startBlock();

                b.startStatement();
                b.startCall("insts", "add");
                b.startNew(types.InstructionTrace);

                b.variable(op.opcodeIdField);
                b.startGroup().variable(fldHitCount).string("[bci]").end();

                int ofs = 1;
                for (int i = 0; i < op.arguments.length; i++) {
                    b.tree(op.arguments[i].createReadCode(vars, ofs));
                }

                b.end(3);

                b.statement("bci += " + op.length());
                b.statement("break");

                b.end();
            }

            b.caseDefault().startCaseBlock();
            b.startThrow().startNew(context.getType(IllegalArgumentException.class)).doubleQuote("Unknown opcode").end(2);
            b.end(); // default case block
            b.end(); // switch block

            b.end(); // while block

            b.startReturn().startNew(types.NodeTrace);
            b.startCall("insts", "toArray").string("new InstructionTrace[0]").end();
            b.end(2);

            vars.bci = null;
        }

        {
            CodeExecutableElement mGetSourceSection = GeneratorUtils.overrideImplement(types.Node, "getSourceSection");
            builderBytecodeNodeType.add(mGetSourceSection);

            CodeTreeBuilder b = mGetSourceSection.createBuilder();

            b.tree(createReparseCheck(typBuilderImpl));

            b.startReturn();
            b.startCall("this", "getSourceSectionImpl");
            b.end(2);
        }

        {
            CodeVariableElement pBci = new CodeVariableElement(context.getType(int.class), "bci");
            CodeExecutableElement mGetSourceSectionAtBci = GeneratorUtils.overrideImplement(types.OperationsNode, "getSourceSectionAtBci");
            builderBytecodeNodeType.add(mGetSourceSectionAtBci);

            CodeTreeBuilder b = mGetSourceSectionAtBci.createBuilder();

            b.tree(createReparseCheck(typBuilderImpl));

            b.startReturn();
            b.startCall("this", "getSourceSectionAtBciImpl");
            b.variable(pBci);
            b.end(2);
        }

        return builderBytecodeNodeType;
    }

    private CodeTree createReparseCheck(CodeTypeElement typBuilderImpl) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.startIf().string("sourceInfo == null").end();
        b.startBlock();
        {
            b.startStatement();
            b.string("OperationsNode reparsed = ");
            b.startStaticCall(typBuilderImpl.asType(), "reparse");
            b.startCall("getLanguage").typeLiteral(m.getLanguageType()).end();
            b.startGroup().cast(m.getParseContextType()).string("parseContext").end();
            b.string("buildOrder");
            b.end(2);

            b.statement("copyReparsedInfo(reparsed)");
        }
        b.end();

        return b.build();
    }

    /**
     * Creates a stack field.
     *
     * @param name the name of the stack field. It will get {@code "Stack"} appended to it.
     * @param argType the type of the stack elements
     * @return the created stack field
     */
    private CodeVariableElement createStackField(String name, TypeMirror argType) {
        TypeMirror stackType = generic(context.getTypeElement(Stack.class), argType);
        CodeVariableElement element = new CodeVariableElement(
                        Set.of(Modifier.PRIVATE, Modifier.FINAL),
                        stackType,
                        name + "Stack");
        CodeTreeBuilder ctb = element.createInitBuilder();
        ctb.string("new Stack<>()");

        return element;
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

        try {
            return List.of(createBuilder(simpleName));
        } catch (Exception e) {
            CodeTypeElement el = GeneratorUtils.createClass(m, null, MOD_PUBLIC_ABSTRACT, simpleName, null);
            CodeTreeBuilder b = el.createDocBuilder();

            b.lineComment(e.getClass().getName() + ": " + e.getMessage());
            for (StackTraceElement ste : e.getStackTrace()) {
                b.lineComment("  at " + ste.toString());
            }

            return List.of(el);
        }
    }

}
