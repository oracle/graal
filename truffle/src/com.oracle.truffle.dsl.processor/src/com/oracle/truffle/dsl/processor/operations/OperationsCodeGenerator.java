package com.oracle.truffle.dsl.processor.operations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

import com.oracle.truffle.dsl.processor.AnnotationProcessor;
import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.generator.CodeTypeElementFactory;
import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.generator.NodeCodeGenerator;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeNames;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.ArrayCodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.DeclaredCodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.operations.Instruction.ExecuteVariables;
import com.oracle.truffle.dsl.processor.operations.Operation.BuilderVariables;
import com.oracle.truffle.dsl.processor.parser.NodeParser;

public class OperationsCodeGenerator extends CodeTypeElementFactory<OperationsData> {

    private ProcessorContext context;
    private AnnotationProcessor<?> processor;
    private OperationsData m;

    private final Set<Modifier> MOD_PUBLIC = Set.of(Modifier.PUBLIC);
    private final Set<Modifier> MOD_PUBLIC_ABSTRACT = Set.of(Modifier.PUBLIC, Modifier.ABSTRACT);
    private final Set<Modifier> MOD_PUBLIC_STATIC = Set.of(Modifier.PUBLIC, Modifier.STATIC);
    private final Set<Modifier> MOD_PUBLIC_STATIC_FINAL = Set.of(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);
    private final Set<Modifier> MOD_PUBLIC_STATIC_ABSTRACT = Set.of(Modifier.PUBLIC, Modifier.STATIC, Modifier.ABSTRACT);
    private final Set<Modifier> MOD_PRIVATE_STATIC_FINAL = Set.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);
    private final Set<Modifier> MOD_PRIVATE_FINAL = Set.of(Modifier.PRIVATE, Modifier.FINAL);
    private final Set<Modifier> MOD_PRIVATE = Set.of(Modifier.PRIVATE);

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

            if (op instanceof Operation.Custom) {
                Operation.Custom customOp = (Operation.Custom) op;
                List<CodeTypeElement> genNode = createOperationNode(customOp.instruction);
                typBuilder.addAll(genNode);
            }
        }

        if (m.hasErrors())
            return typBuilder;

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

    /**
     * Create the implementation class. This class implements the begin/end methods, and in general
     * does the most of the build-time heavy lifting.
     */
    private CodeTypeElement createBuilderImpl(CodeTypeElement typBuilder) {
        String simpleName = m.getTemplateType().getSimpleName() + "BuilderinoImpl";
        CodeTypeElement typBuilderImpl = GeneratorUtils.createClass(m, null, Set.of(Modifier.PRIVATE, Modifier.STATIC), simpleName, typBuilder.asType());

        DeclaredType byteArraySupportType = context.getDeclaredType("com.oracle.truffle.api.memory.ByteArraySupport");
        CodeVariableElement leBytes = new CodeVariableElement(
                        MOD_PRIVATE_STATIC_FINAL,
                        byteArraySupportType, "LE_BYTES");
        leBytes.createInitBuilder().startStaticCall(byteArraySupportType, "littleEndian").end();
        typBuilderImpl.add(leBytes);

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

        CodeVariableElement fldLastPush = new CodeVariableElement(context.getType(int.class), "lashPush");
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

            b.startReturn();
            b.startNew(builderBytecodeNodeType.asType());
            b.variable(fldMaxStack);
            b.startGroup().variable(fldMaxLocal).string(" + 1").end();
            b.string("bcCopy");
            b.string("cpCopy");
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

            CodeVariableElement varChildIndex = new CodeVariableElement(context.getType(int.class), "childIndex");
            b.declaration("int", varChildIndex.getName(), "childIndexStack.peek()");

            vars.childIndex = varChildIndex;

            b.startSwitch().startCall(fldTypeStack, "peek").end(2);
            b.startBlock();

            for (Operation parentOp : m.getOperations()) {

                CodeTree afterChild = parentOp.createBeforeChildCode(vars);
                if (afterChild == null)
                    continue;

                b.startCase().string(parentOp.id + " /* " + parentOp.name + " */").end();
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

                b.startCase().string(parentOp.id + " /* " + parentOp.name + " */").end();
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

                CodeExecutableElement metEnd = GeneratorUtils.overrideImplement(typBuilder, "end" + op.name);
                typBuilderImpl.add(metEnd);

                {
                    // doBeforeChild();

                    // typeStack.push(ID);
                    // childIndexStack.push(0);

                    // Object[] args = new Object[...];
                    // args[x] = arg_x;...
                    // argumentStack.push(args);

                    // << begin >>

                    CodeTreeBuilder b = metBegin.getBuilder();

                    b.startStatement().startCall(fldTypeStack, "push").string("" + op.id).end(2);
                    b.startStatement().startCall(fldChildIndexStack, "push").string("0").end(2);

                    vars.arguments = metBegin.getParameters().toArray(new CodeVariableElement[0]);

                    if (vars.arguments.length > 0) {
                        b.declaration("Object[]", "args", "new Object[" + vars.arguments.length + "]");
                        for (int i = 0; i < vars.arguments.length; i++) {
                            b.statement("args[" + i + "] = " + vars.arguments[i].getName());
                        }

                        b.startStatement().startCall(fldArgumentStack, "push").string("args").end(2);
                    }

                    b.statement("doBeforeChild()");
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

                    b.startAssert().startCall(fldTypeStack, "pop").end().string(" == " + op.id).end();

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

                        int actualParamIndex = 0;

                        CodeVariableElement[] varArgs = new CodeVariableElement[argInfo.size()];
                        for (int i = 0; i < argInfo.size(); i++) {
                            if (argInfo.get(i).isImplicit())
                                continue;

                            varArgs[i] = new CodeVariableElement(argInfo.get(i).toBuilderArgumentType(), "arg_" + i);

                            CodeTreeBuilder b2 = CodeTreeBuilder.createBuilder();
                            b2.maybeCast(context.getType(Object.class), argInfo.get(i).toBuilderArgumentType());
                            b2.string("args[" + i + "]");

                            b.declaration(argInfo.get(i).toBuilderArgumentType(), "arg_" + i, b2.build());
                        }

                        vars.arguments = varArgs;
                    }

                    b.tree(op.createEndCode(vars));
                    b.startAssign(fldLastPush).tree(op.createPushCountCode(vars)).end();

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
        CodeTypeElement builderBytecodeNodeType = GeneratorUtils.createClass(m, null, Set.of(Modifier.PRIVATE, Modifier.STATIC), simpleName, types.OperationsNode);

        CodeVariableElement fldBc = new CodeVariableElement(MOD_PRIVATE_FINAL, arrayOf(context.getType(byte.class)), "bc");
        builderBytecodeNodeType.add(fldBc);

        CodeVariableElement fldConsts = new CodeVariableElement(Set.of(Modifier.PRIVATE, Modifier.FINAL), arrayOf(context.getType(Object.class)), "consts");
        builderBytecodeNodeType.add(fldConsts);

        builderBytecodeNodeType.add(GeneratorUtils.createConstructorUsingFields(Set.of(), builderBytecodeNodeType));

        ExecuteVariables vars = new ExecuteVariables();
        vars.bc = fldBc;
        vars.consts = fldConsts;
        vars.maxStack = new CodeVariableElement(context.getType(int.class), "maxStack");

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

            CodeTreeBuilder b = mContinueAt.getBuilder();

            CodeVariableElement varFunArgs = new CodeVariableElement(arrayOf(context.getType(Object.class)), "funArgs");
            CodeVariableElement varSp = new CodeVariableElement(context.getType(int.class), "sp");
            CodeVariableElement varBci = new CodeVariableElement(context.getType(int.class), "bci");

            b.declaration("final Object[]", varFunArgs.getName(), "frame.getArguments()");
            b.declaration("int", varSp.getName(), "0");
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

            b.startSwitch().string("bc[bci]").end();
            b.startBlock();

            for (Instruction op : m.getInstructions()) {
                b.startCase().string(op.id + " /* " + op.name + " */").end();
                b.startBlock();

                b.tree(op.createExecuteCode(vars));
                b.tree(op.createExecuteEpilogue(vars));

                b.end();
            }

            b.caseDefault().startCaseBlock().tree(GeneratorUtils.createShouldNotReachHere("unknown opcode encountered")).end();

            b.end(); // switch block

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

            CodeTreeBuilder b = mDump.getBuilder();
            CodeVariableElement varSb = new CodeVariableElement(context.getType(StringBuilder.class), "sb");

            b.declaration("int", "bci", "0");
            b.declaration("StringBuilder", "sb", "new StringBuilder()");

            vars.bci = new CodeVariableElement(context.getType(int.class), "bci");

            b.startWhile().string("bci < bc.length").end();
            b.startBlock(); // while block

            b.statement("sb.append(String.format(\" %04x \", bci))");

            b.startSwitch().string("bc[bci]").end();
            b.startBlock();

            for (Instruction op : m.getInstructions()) {
                b.startCase().string("" + op.id).end();
                b.startBlock();

                b.statement("sb.append(\"" + op.name + " \")");

                int ofs = 1;
                for (int i = 0; i < op.arguments.length; i++) {
                    b.startStatement().string("sb.append(").tree(op.arguments[i].createReadCode(vars, ofs)).string(")").end();
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

            b.startReturn().string("sb.toString()").end();

            vars.bci = null;

            builderBytecodeNodeType.add(mDump);
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

    private List<CodeTypeElement> createOperationNode(Instruction.Custom instruction) {
        TypeElement t = instruction.type;

        PackageElement pack = ElementUtils.findPackageElement(t);
        CodeTypeElement typProxy = new CodeTypeElement(MOD_PUBLIC_STATIC_ABSTRACT, ElementKind.CLASS, pack,
                        t.getSimpleName() + "Gen");

        typProxy.setSuperClass(types.Node);
        typProxy.addAnnotationMirror(new CodeAnnotationMirror(types.GenerateUncached));
        GeneratorUtils.addGeneratedBy(context, typProxy, t);

        {
            CodeExecutableElement metExecute = new CodeExecutableElement(MOD_PUBLIC_ABSTRACT,
                            context.getType(Object.class), "execute");
            typProxy.add(metExecute);

            for (int i = 0; i < instruction.stackPops; i++) {
                CodeVariableElement par;
                if (i == instruction.stackPops - 1 && instruction.isVarArgs) {
                    par = new CodeVariableElement(arrayOf(context.getType(Object.class)), "arg" + i);
                } else {
                    par = new CodeVariableElement(context.getType(Object.class), "arg" + i);
                }
                metExecute.addParameter(par);
            }
        }

        for (ExecutableElement el : ElementFilter.methodsIn(instruction.type.getEnclosedElements())) {
            if (el.getModifiers().containsAll(MOD_PUBLIC_STATIC)) {
                CodeExecutableElement metProxy = new CodeExecutableElement(el.getModifiers(), el.getReturnType(),
                                el.getSimpleName().toString());
                for (VariableElement par : el.getParameters()) {
                    metProxy.addParameter(par);
                }
                for (AnnotationMirror ann : el.getAnnotationMirrors()) {
                    metProxy.addAnnotationMirror(ann);
                }

                CodeTreeBuilder b = metProxy.getBuilder();

                if (metProxy.getReturnType().getKind() != TypeKind.VOID) {
                    b.startReturn();
                }

                b.startStaticCall(el);
                for (VariableElement par : el.getParameters()) {
                    b.variable(par);
                }
                b.end();

                if (metProxy.getReturnType().getKind() != TypeKind.VOID) {
                    b.end();
                }

                typProxy.add(metProxy);
            }
        }

        List<CodeTypeElement> result = new ArrayList<>(2);
        result.add(typProxy);

        NodeParser parser = NodeParser.createDefaultParser();
        NodeData data = parser.parse(typProxy);
        if (data == null) {
            m.addError(t, "Could not generate node data");
            return List.of();
        }
        NodeCodeGenerator gen = new NodeCodeGenerator();
        CodeTypeElement genResult = gen.create(context, processor, data).get(0);

        CodeTypeElement genUncached = null;

        for (TypeElement el : ElementFilter.typesIn(genResult.getEnclosedElements())) {
            if (el.getSimpleName().toString().equals("Uncached")) {
                genUncached = (CodeTypeElement) el;
                break;
            }
        }

        assert genUncached != null;

        genUncached.setSimpleName(CodeNames.of(t.getSimpleName() + "Uncached"));
        GeneratorUtils.addGeneratedBy(context, genUncached, t);

        {
            CodeVariableElement fldInstance = new CodeVariableElement(MOD_PUBLIC_STATIC_FINAL, genUncached.asType(), "INSTANCE");
            CodeTreeBuilder b = fldInstance.createInitBuilder();
            b.startNew(genUncached.asType());
            b.end();
            genUncached.add(fldInstance);

            instruction.setUncachedInstance(fldInstance);
        }

        result.add(genUncached);

        return result;
    }

    private static TypeMirror generic(TypeElement el, TypeMirror... params) {
        return new DeclaredCodeTypeMirror(el, Arrays.asList(params));
    }

    private static TypeMirror arrayOf(TypeMirror el) {
        return new ArrayCodeTypeMirror(el);
    }

    @Override
    public List<CodeTypeElement> create(ProcessorContext context, AnnotationProcessor<?> processor, OperationsData m) {
        this.context = context;
        this.processor = processor;
        this.m = m;

        String simpleName = m.getTemplateType().getSimpleName() + "Builderino";

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
