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
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationValue;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeNames;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.ArrayCodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.DeclaredCodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.operations.Instruction.ArgumentType;
import com.oracle.truffle.dsl.processor.operations.Instruction.ExecutorVariables;
import com.oracle.truffle.dsl.processor.operations.Operation.EmitterVariables;
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
            TypeMirror[] args = op.getBuilderArgumentTypes(context, types);
            CodeVariableElement[] params = new CodeVariableElement[args.length];

            for (int i = 0; i < params.length; i++) {
                params[i] = new CodeVariableElement(args[i], "arg" + i);
            }

            if (op.hasChildren()) {
                CodeExecutableElement metBegin = new CodeExecutableElement(MOD_PUBLIC_ABSTRACT, context.getType(void.class), "begin" + op.getName(), params);
                typBuilder.add(metBegin);

                CodeExecutableElement metEnd = new CodeExecutableElement(MOD_PUBLIC_ABSTRACT, context.getType(void.class), "end" + op.getName());
                typBuilder.add(metEnd);
            } else {
                CodeExecutableElement metEmit = new CodeExecutableElement(MOD_PUBLIC_ABSTRACT, context.getType(void.class), "emit" + op.getName(), params);
                typBuilder.add(metEmit);
            }

            if (op instanceof Operation.CustomOperation) {
                Operation.CustomOperation customOp = (Operation.CustomOperation) op;
                List<CodeTypeElement> genNode = createOperationNode(customOp.getCustomInstruction());
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

        CodeTypeElement builderNodeType = createBuilderImplNode(simpleName + "Node");
        typBuilderImpl.add(builderNodeType);

        CodeTypeElement builderLabelImplType = createBuilderLabelImpl(simpleName + "Label");
        typBuilderImpl.add(builderLabelImplType);

        CodeTypeElement builderBytecodeNodeType = createBuilderBytecodeNode(simpleName + "BytecodeNode");
        typBuilderImpl.add(builderBytecodeNodeType);

        CodeVariableElement childStackField = createStackField("child", generic(context.getTypeElement(ArrayList.class), builderNodeType.asType()));
        typBuilderImpl.add(childStackField);

        CodeVariableElement argumentStackField = createStackField("argument", new ArrayCodeTypeMirror(context.getType(Object.class)));
        typBuilderImpl.add(argumentStackField);

        CodeVariableElement typeStackField = createStackField("type", context.getType(Integer.class));
        typBuilderImpl.add(typeStackField);

        {
            CodeExecutableElement metReset = new CodeExecutableElement(
                            Set.of(Modifier.PUBLIC),
                            context.getType(void.class), "reset");
            typBuilderImpl.add(metReset);

            CodeTreeBuilder b = metReset.getBuilder();
            b.startStatement().startCall(childStackField.getName(), "clear").end(2);
            b.startStatement().startCall(childStackField.getName(), "add").string("new ArrayList<>()").end(2);
            b.startStatement().startCall(argumentStackField.getName(), "clear").end(2);
            b.startStatement().startCall(typeStackField.getName(), "clear").end(2);
        }

        {
            CodeExecutableElement mBuild = new CodeExecutableElement(Set.of(Modifier.PUBLIC), types.OperationsNode, "build");
            typBuilderImpl.add(mBuild);

            CodeTreeBuilder b = mBuild.getBuilder();

            b.startAssert();
            b.string("childStack.size() == 1");
            b.end();

            b.declaration(arrayOf(builderNodeType.asType()), "operations", "childStack.get(0).toArray(new " + builderNodeType.getSimpleName() + "[0])");
            b.declaration(builderNodeType.asType(), "rootNode", "new " + builderNodeType.getSimpleName() + "(0, new Object[0], operations)");
            b.declaration("byte[]", "bc", "new byte[65535]");
            b.declaration(types.OperationsConstantPool, "constPool", "new OperationsConstantPool()");
            b.declaration("int", "len", "rootNode.build(bc, 0, constPool)");
            b.declaration("byte[]", "bcCopy", "java.util.Arrays.copyOf(bc, len)");
            b.declaration("Object[]", "cpCopy", "constPool.getValues()");

            b.startReturn();
            b.startNew(builderBytecodeNodeType.asType());
            b.string("rootNode.maxStack");
            b.string("rootNode.maxLocals");
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
            CodeExecutableElement metDoBeginOperation = new CodeExecutableElement(MOD_PRIVATE, context.getType(void.class), "doBeginOperation");
            metDoBeginOperation.addParameter(new CodeVariableElement(context.getType(int.class), "type"));
            metDoBeginOperation.addParameter(new CodeVariableElement(context.getType(Object.class), "...arguments"));

            CodeTreeBuilder builder = metDoBeginOperation.getBuilder();

            builder.statement("typeStack.add(type)");
            builder.statement("childStack.add(new ArrayList<>())");
            builder.statement("argumentStack.add(arguments)");

            typBuilderImpl.add(metDoBeginOperation);
        }

        {
            CodeExecutableElement doEndOperationMethod = new CodeExecutableElement(MOD_PRIVATE, context.getType(void.class), "doEndOperation");
            doEndOperationMethod.addParameter(new CodeVariableElement(context.getType(int.class), "type"));

            CodeTreeBuilder builder = doEndOperationMethod.getBuilder();

            builder.statement("int topType = typeStack.pop()");

            builder.startAssert();
            builder.string("topType == type");
            builder.end();

            builder.statement("Object[] args = argumentStack.pop()");

            builder.declaration(new ArrayCodeTypeMirror(builderNodeType.asType()), "children",
                            "childStack.pop().toArray(new " + builderNodeType.getSimpleName() + "[0])");

            builder.declaration(builderNodeType.asType(), "result",
                            "new " + builderNodeType.getSimpleName() + "(type, args, children)");

            builder.statement("childStack.peek().add(result)");

            typBuilderImpl.add(doEndOperationMethod);

        }

        {
            CodeExecutableElement doEmitOperationMethod = new CodeExecutableElement(MOD_PRIVATE, context.getType(void.class), "doEmitOperation");
            doEmitOperationMethod.addParameter(new CodeVariableElement(context.getType(int.class), "type"));
            doEmitOperationMethod.addParameter(new CodeVariableElement(context.getType(Object.class), "...arguments"));

            CodeTreeBuilder builder = doEmitOperationMethod.getBuilder();

            builder.declaration(builderNodeType.asType(), "result",
                            "new " + builderNodeType.getSimpleName() + "(type, arguments, new " + builderNodeType.getSimpleName() + "[0])");

            builder.startStatement();
            builder.string("childStack.peek().add(result)");
            builder.end();

            typBuilderImpl.add(doEmitOperationMethod);
        }

        {
            CodeExecutableElement mCreateLabel = GeneratorUtils.overrideImplement(typBuilder, "createLabel");

            CodeTreeBuilder b = mCreateLabel.getBuilder();
            b.startReturn();
            b.startNew(builderLabelImplType.asType());
            b.end(2);

            typBuilderImpl.add(mCreateLabel);
        }

        for (Operation op : m.getOperations()) {
            TypeMirror[] args = op.getBuilderArgumentTypes(context, types);
            CodeVariableElement[] params = new CodeVariableElement[args.length];

            for (int i = 0; i < params.length; i++) {
                params[i] = new CodeVariableElement(args[i], "arg" + i);
            }

            if (op.hasChildren()) {
                {
                    CodeExecutableElement metBegin = GeneratorUtils.overrideImplement(typBuilder, "begin" + op.getName());
                    CodeTreeBuilder b = metBegin.getBuilder();
                    b.startStatement().startCall("doBeginOperation");
                    b.string("" + op.getType());
                    for (int i = 0; i < params.length; i++) {
                        b.string("arg" + i);
                    }
                    b.end(2);
                    typBuilderImpl.add(metBegin);
                }

                {
                    CodeExecutableElement metEnd = GeneratorUtils.overrideImplement(typBuilder, "end" + op.getName());
                    CodeTreeBuilder b = metEnd.getBuilder();
                    b.startStatement().startCall("doEndOperation");
                    b.string("" + op.getType());
                    b.end(2);
                    typBuilderImpl.add(metEnd);
                }
            } else {
                CodeExecutableElement metEmit = GeneratorUtils.overrideImplement(typBuilder, "emit" + op.getName());
                {
                    CodeTreeBuilder b = metEmit.getBuilder();
                    b.startStatement().startCall("doEmitOperation");
                    b.string("" + op.getType());
                    for (int i = 0; i < params.length; i++) {
                        b.string("arg" + i);
                    }
                    b.end(2);
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
     * Create the BuilderNode class.
     *
     * This class represents the built method internally. Right now it only supports generating the
     * bytecode, but in the future it will support analysis and finding super-instructions.
     */
    private CodeTypeElement createBuilderImplNode(String simpleName) {
        CodeTypeElement builderNodeType = GeneratorUtils.createClass(m, null, Set.of(Modifier.PRIVATE, Modifier.STATIC), simpleName, null);

        CodeVariableElement fldType = new CodeVariableElement(MOD_PRIVATE_FINAL, context.getType(int.class), "type");
        builderNodeType.add(fldType);

        CodeVariableElement fldArguments = new CodeVariableElement(MOD_PRIVATE_FINAL, arrayOf(context.getType(Object.class)), "arguments");
        builderNodeType.add(fldArguments);

        CodeVariableElement fldChildren = new CodeVariableElement(MOD_PRIVATE_FINAL, arrayOf(builderNodeType.asType()), "children");
        builderNodeType.add(fldChildren);

        CodeExecutableElement ctor = GeneratorUtils.createConstructorUsingFields(Set.of(), builderNodeType);
        builderNodeType.add(ctor);

        CodeVariableElement fldReturnsValue = new CodeVariableElement(Set.of(Modifier.PRIVATE, Modifier.FINAL), context.getType(int.class), "returnsValue");
        builderNodeType.add(fldReturnsValue);

        CodeVariableElement fldMaxStack = new CodeVariableElement(Set.of(Modifier.PRIVATE, Modifier.FINAL), context.getType(int.class), "maxStack");
        builderNodeType.add(fldMaxStack);

        CodeVariableElement fldMaxLocals = new CodeVariableElement(Set.of(Modifier.PRIVATE, Modifier.FINAL), context.getType(int.class), "maxLocals");
        builderNodeType.add(fldMaxLocals);

        {
            CodeTreeBuilder b = ctor.getBuilder();

            b.startSwitch().field("this", fldType).end().startBlock();

            Operation.CtorVariables vars = new Operation.CtorVariables(fldChildren, fldArguments, fldReturnsValue, fldMaxStack, fldMaxLocals);

            for (Operation op : m.getOperations()) {
                b.startCase().string("" + op.getType() + " /* " + op.getName() + " */").end();

                b.startBlock();

                b.tree(op.createCtorCode(types, vars));

                b.statement("int maxStack_ = 1");
                b.statement("int maxLocals_ = 0");

                if (op.children == Operation.VARIABLE_CHILDREN) {
                    b.startFor().string("int i = 0; i < children.length; i++").end();
                    b.startBlock();

                    String extra = op.keepsChildValues() ? " + i" : "";
                    b.statement("if (maxStack_ < children[i].maxStack" + extra + ") maxStack_ = children[i].maxStack" + extra);

                    b.statement("if (maxLocals_ < children[i].maxLocals) maxLocals_ = children[i].maxLocals");

                    b.end();
                } else {
                    for (int i = 0; i < op.children; i++) {
                        String extra = op.keepsChildValues() ? " + " + i : "";
                        b.statement("if (maxStack_ < children[" + i + "].maxStack" + extra + ") maxStack_ = children[" + i + "].maxStack" + extra);

                        b.statement("if (maxLocals_ < children[" + i + "].maxLocals) maxLocals_ = children[" + i + "].maxLocals");
                    }
                }

                int argIdx = 0;
                for (ArgumentType arg : op.getArgumentTypes()) {
                    if (arg == ArgumentType.LOCAL || arg == ArgumentType.LOCAL_INDEX) {
                        b.statement("if (maxLocals_ < (short) arguments[" + argIdx + "] + 1) maxLocals_ = (short) arguments[" + argIdx + "] + 1");
                    }
                    argIdx++;
                }

                b.statement("maxStack = maxStack_");
                b.statement("maxLocals = maxLocals_");

                b.statement("System.out.println(\"" + op.getName() + " \" + maxStack + \" \" + maxLocals)");

                b.startStatement().string("break").end();
                b.end();
            }

            b.caseDefault().startCaseBlock();
            b.tree(GeneratorUtils.createShouldNotReachHere());
            b.end(); // default case block

            b.end();
        }

        {
            CodeVariableElement argBc = new CodeVariableElement(arrayOf(context.getType(byte.class)), "bc");
            CodeVariableElement argStartBci = new CodeVariableElement(context.getType(int.class), "startBci");
            CodeVariableElement argConstPool = new CodeVariableElement(types.OperationsConstantPool, "constPool");
            CodeExecutableElement metBuild = new CodeExecutableElement(Set.of(), context.getType(int.class), "build", argBc, argStartBci, argConstPool);

            {
                DeclaredType typSw = context.getDeclaredType(SuppressWarnings.class);
                CodeAnnotationMirror annSw = new CodeAnnotationMirror(typSw);
                annSw.setElementValue(annSw.findExecutableElement("value"), new CodeAnnotationValue("cast"));
                metBuild.addAnnotationMirror(annSw);
            }

            CodeTreeBuilder b = metBuild.getBuilder();

            CodeVariableElement varBci = new CodeVariableElement(context.getType(int.class), "bci");
            b.declaration("int", varBci.getName(), argStartBci.getName());

            b.startSwitch().field("this", fldType).end().startBlock();

            for (Operation op : m.getOperations()) {
                b.startCase().string("" + op.getType() + " /* " + op.getName() + " */").end();

                b.startBlock();

                EmitterVariables vars = new EmitterVariables(metBuild, argBc, varBci, argConstPool, fldChildren, fldArguments);

                b.tree(op.createEmitterCode(types, vars));

                b.startStatement().string("break").end();
                b.end();
            }

            b.end(); // switchBlock

            b.startReturn().string("bci").end();

            builderNodeType.add(metBuild);
        }

        return builderNodeType;
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

            b.startSwitch().string("bc[bci]").end();
            b.startBlock();

            ExecutorVariables vars = new ExecutorVariables(varBci, varNextBci, fldBc, fldConsts, varSp, varFunArgs, argFrame, varReturnValue);

            for (Instruction op : m.getInstructions()) {

                b.startCase().string("" + op.opcodeNumber + " /* " + op.getClass().getSimpleName() + " */").end();

                b.startBlock();

                CodeVariableElement[] pops = new CodeVariableElement[op.stackPops];
                for (int i = op.stackPops - 1; i >= 0; i--) {
                    pops[i] = new CodeVariableElement(context.getType(Object.class), "value" + i);
                    b.declaration("Object", pops[i].getName(), argFrame.getName() + ".getValue(--" + varSp.getName() + ")");
                }

                CodeVariableElement[] args = new CodeVariableElement[op.arguments.length];
                int ofs = 1;
                for (int i = 0; i < op.arguments.length; i++) {
                    args[i] = new CodeVariableElement(op.arguments[i].toExecType(context, types), "arg" + i);
                    b.declaration(args[i].asType(), args[i].getName(),
                                    op.arguments[i].createReaderCode(vars, CodeTreeBuilder.singleString(varBci.getName() + " + " + ofs)));
                    ofs += op.arguments[i].length;
                }

                vars.arguments = args;
                vars.children = pops;
                if (op.stackPushes > 0) {
                    vars.result = new CodeVariableElement(context.getType(Object.class), "result");
                    b.statement("Object " + vars.result.getName());
                }

                b.tree(op.createExecutorCode(types, vars));

                if (op.stackPushes > 0) {
                    b.statement(argFrame.getName() + ".setObject(" + varSp.getName() + "++, " + vars.result.getName() + ")");
                }

                if (!op.isDivergent()) {
                    b.tree(op.createNextBciCode(types, vars));
                    b.statement("break");
                }

                b.end();
            }

            b.caseDefault().startCaseBlock().tree(GeneratorUtils.createShouldNotReachHere("unknown opcode encountered")).end();

            b.end(); // switch block

            b.statement("bci = nextBci");
            b.end(); // while block

            b.startReturn().string("returnValue").end();

            builderBytecodeNodeType.add(mContinueAt);
        }

        {
            CodeExecutableElement mDump = new CodeExecutableElement(Set.of(Modifier.PUBLIC), context.getType(String.class), "dump");

            CodeTreeBuilder b = mDump.getBuilder();
            CodeVariableElement varSb = new CodeVariableElement(context.getType(StringBuilder.class), "sb");

            b.declaration("int", "bci", "0");
            b.declaration("StringBuilder", "sb", "new StringBuilder()");

            b.startWhile().string("bci < bc.length").end();
            b.startBlock(); // while block

            b.statement("sb.append(String.format(\" %04x \", bci))");

            b.startSwitch().string("bc[bci]").end();
            b.startBlock();

            for (Instruction op : m.getInstructions()) {
                b.startCase().string("" + op.opcodeNumber).end();
                b.startBlock();

                b.statement("sb.append(\"" + op.getClass().getSimpleName() + " \")");

                CodeVariableElement[] args = new CodeVariableElement[op.arguments.length];
                int ofs = 1;
                for (int i = 0; i < op.arguments.length; i++) {
                    args[i] = new CodeVariableElement(op.arguments[i].toExecType(context, types), "arg" + i);
                    b.tree(op.arguments[i].createDumperCode(fldBc, CodeTreeBuilder.singleString("bci + " + ofs), varSb));
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

    private List<CodeTypeElement> createOperationNode(Instruction.Custom instr) {
        TypeElement t = instr.getType();

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

            for (int i = 0; i < instr.stackPops; i++) {
                metExecute.addParameter(new CodeVariableElement(context.getType(Object.class), "arg" + i));
            }
        }

        for (ExecutableElement el : ElementFilter.methodsIn(instr.getType().getEnclosedElements())) {
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

            instr.setUncachedInstance(fldInstance);
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
