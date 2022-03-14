package com.oracle.truffle.dsl.processor.operations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.Stack;

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

    private final Set<Modifier> MOD_PUBLIC_ABSTRACT = Set.of(Modifier.PUBLIC, Modifier.ABSTRACT);
    private final Set<Modifier> MOD_PUBLIC_STATIC = Set.of(Modifier.PUBLIC, Modifier.STATIC);
    private final Set<Modifier> MOD_PRIVATE_STATIC_FINAL = Set.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);
    private final Set<Modifier> MOD_PRIVATE_FINAL = Set.of(Modifier.PRIVATE, Modifier.FINAL);

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
            CodeExecutableElement metCreateBuilder = new CodeExecutableElement(MOD_PUBLIC_STATIC, typBuilder.asType(), "createBuilder");
            CodeTreeBuilder b = metCreateBuilder.getBuilder();
            b.startReturn().startNew(typBuilderImpl.asType()).end(2);
            typBuilder.add(metCreateBuilder);
        }

        return typBuilder;
    }

    private CodeTypeElement createBuilderImpl(CodeTypeElement typBuilder) {
        String simpleName = m.getTemplateType().getSimpleName() + "BuilderImpl";
        CodeTypeElement typBuilderImpl = GeneratorUtils.createClass(m, null, Set.of(Modifier.PRIVATE, Modifier.STATIC), simpleName, typBuilder.asType());
        typBuilderImpl.setEnclosingElement(typBuilder);

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

        CodeTypeElement builderBytecodeNodeType = createBuilderBytecodeNode(simpleName + "BytecodeNode");
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

        CodeVariableElement fldConstPool = new CodeVariableElement(types.OperationsConstantPool, "constPool");
        typBuilderImpl.add(fldConstPool);
        {
            CodeTreeBuilder b = fldConstPool.createInitBuilder();
            b.startNew(types.OperationsConstantPool).end();
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
            b.startStatement().startCall(fldChildIndexStack.getName(), "clear").end(2);
            b.startStatement().startCall(fldChildIndexStack.getName(), "add").string("0").end(2);
            b.startStatement().startCall(fldArgumentStack.getName(), "clear").end(2);
            b.startStatement().startCall(fldTypeStack.getName(), "clear").end(2);
            b.startStatement().startCall(fldTypeStack.getName(), "add").string("0").end(2);
            b.startStatement().startCall(fldExceptionHandlers.getName(), "clear").end(2);
        }

        {
            CodeExecutableElement mBuild = new CodeExecutableElement(Set.of(Modifier.PUBLIC), types.OperationsNode, "build");
            typBuilderImpl.add(mBuild);

            CodeTreeBuilder b = mBuild.getBuilder();

            b.startAssert();
            b.string(fldChildIndexStack.getName() + ".size() == 1");
            b.end();

            b.declaration("byte[]", "bcCopy", "java.util.Arrays.copyOf(bc, bci)");
            b.declaration("Object[]", "cpCopy", "constPool.getValues()");
            b.declaration("BuilderExceptionHandler[]", "handlers", fldExceptionHandlers.getName() + ".toArray(new BuilderExceptionHandler[0])");

            b.startReturn();
            b.startNew(builderBytecodeNodeType.asType());
            b.variable(fldMaxStack);
            b.startGroup().variable(fldMaxLocal).string(" + 1").end();
            b.string("bcCopy");
            b.string("cpCopy");
            b.string("handlers");
            b.end(2);

        }

        {
            CodeExecutableElement ctor = new CodeExecutableElement(null, simpleName);

            CodeTreeBuilder builder = ctor.getBuilder();
            builder.startStatement().string("reset()").end();

            typBuilderImpl.add(ctor);
        }

        {
            CodeExecutableElement mCreateLabel = GeneratorUtils.overrideImplement(typBuilder, "createLabel");

            CodeTreeBuilder b = mCreateLabel.getBuilder();
            b.startReturn();
            b.startNew(builderLabelImplType.asType());
            b.end(2);

            typBuilderImpl.add(mCreateLabel);
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

                    b.startAssert().startCall(fldTypeStack, "pop").end().string(" == ").variable(op.idConstantField).end();

                    vars.numChildren = new CodeVariableElement(context.getType(int.class), "numChildren");
                    b.declaration("int", "numChildren", "childIndexStack.pop()");

                    if (!op.isVariableChildren()) {
                        b.startAssert();
                        b.string("numChildren == " + op.children + " : ");
                        b.doubleQuote(op.name + " expected " + op.children + " children, got ");
                        b.string(" + numChildren");
                        b.end();
                    } else {
                        b.startAssert();
                        b.string("numChildren > " + op.minimumChildren() + " : ");
                        b.doubleQuote(op.name + " expected at least " + op.minimumChildren() + " children, got ");
                        b.string(" + numChildren");
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
    private CodeTypeElement createBuilderBytecodeNode(String simpleName) {
        CodeTypeElement builderBytecodeNodeType = GeneratorUtils.createClass(m, null, MOD_PRIVATE_STATIC_FINAL, simpleName, types.OperationsNode);

        CodeVariableElement fldBc = new CodeVariableElement(MOD_PRIVATE_FINAL, arrayOf(context.getType(byte.class)), "bc");
        builderBytecodeNodeType.add(fldBc);

        CodeVariableElement fldConsts = new CodeVariableElement(MOD_PRIVATE_FINAL, arrayOf(context.getType(Object.class)), "consts");
        builderBytecodeNodeType.add(fldConsts);

        CodeVariableElement fldHandlers = new CodeVariableElement(MOD_PRIVATE_FINAL, arrayOf(types.BuilderExceptionHandler), "handlers");
        builderBytecodeNodeType.add(fldHandlers);

        CodeExecutableElement ctor = GeneratorUtils.createConstructorUsingFields(Set.of(), builderBytecodeNodeType);
        builderBytecodeNodeType.add(ctor);

        CodeVariableElement fldHitCount = null;
        if (m.isTracing()) {
            fldHitCount = new CodeVariableElement(MOD_PRIVATE_FINAL, arrayOf(context.getType(int.class)), "hitCount");
            builderBytecodeNodeType.add(fldHitCount);
        }

        {
            CodeTreeBuilder b = ctor.getBuilder();

            if (m.isTracing()) {
                b.startAssign(fldHitCount).startNewArray(
                                (ArrayType) fldHitCount.getType(),
                                CodeTreeBuilder.createBuilder().variable(fldBc).string(".length").build());
                b.end(2);
            }
        }

        ExecuteVariables vars = new ExecuteVariables();
        vars.bytecodeNodeType = builderBytecodeNodeType;
        vars.bc = fldBc;
        vars.consts = fldConsts;
        vars.maxStack = new CodeVariableElement(context.getType(int.class), "maxStack");
        vars.handlers = fldHandlers;

        {
            CodeExecutableElement mExecute = new CodeExecutableElement(Set.of(Modifier.PUBLIC), context.getType(Object.class), "execute", new CodeVariableElement(types.VirtualFrame, "frame"));

            CodeTreeBuilder builder = mExecute.getBuilder();
            builder.startReturn();
            builder.startCall("continueAt");

            builder.string("frame");
            builder.string("null");

            builder.end(2);

            builderBytecodeNodeType.add(mExecute);
        }

        {
            CodeVariableElement argFrame = new CodeVariableElement(types.VirtualFrame, "frame");
            CodeVariableElement argStartIndex = new CodeVariableElement(types.OperationLabel, "startIndex");
            CodeExecutableElement mContinueAt = new CodeExecutableElement(
                            Set.of(Modifier.PUBLIC), context.getType(Object.class), "continueAt",
                            argFrame, argStartIndex);

            {
                CodeAnnotationMirror annExplodeLoop = new CodeAnnotationMirror(types.ExplodeLoop);
                mContinueAt.addAnnotationMirror(annExplodeLoop);
                annExplodeLoop.setElementValue("kind", new CodeAnnotationValue(new CodeVariableElement(
                                context.getDeclaredType("com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind"), "MERGE_EXPLODE")));
            }

            CodeTreeBuilder b = mContinueAt.getBuilder();

            CodeVariableElement varSp = new CodeVariableElement(context.getType(int.class), "sp");
            CodeVariableElement varBci = new CodeVariableElement(context.getType(int.class), "bci");

            b.declaration("int", varSp.getName(), "maxLocals");
            b.declaration("int", varBci.getName(), "0");

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

            b.startTryBlock();

            if (m.isTracing()) {
                b.startStatement().variable(fldHitCount).string("[bci]++").end();
            }

            b.startSwitch().string("bc[bci]").end();
            b.startBlock();

            for (Instruction op : m.getInstructions()) {
                b.startCase().variable(op.opcodeIdField).end();
                b.startBlock();

                b.tree(op.createExecuteCode(vars));
                b.tree(op.createExecuteEpilogue(vars));

                b.end();
            }

            b.caseDefault().startCaseBlock().tree(GeneratorUtils.createShouldNotReachHere("unknown opcode encountered")).end();

            b.end(); // switch block

            b.end().startCatchBlock(context.getDeclaredType("com.oracle.truffle.api.exception.AbstractTruffleException"), "ex");

            b.startFor().string("int handlerIndex = 0; handlerIndex < " + vars.handlers.getName() + ".length; handlerIndex++").end();
            b.startBlock();

            b.declaration(types.BuilderExceptionHandler, "handler", vars.handlers.getName() + "[handlerIndex]");
            b.startIf().string("handler.startBci > bci || handler.endBci <= bci").end();
            b.statement("continue");

            b.startAssign(varSp).string("handler.startStack").end();
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

            b.startReturn().string("returnValue").end();

            vars.bci = null;
            vars.nextBci = null;
            vars.frame = null;
            vars.sp = null;
            vars.returnValue = null;

            builderBytecodeNodeType.add(mContinueAt);
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

        return builderBytecodeNodeType;
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
