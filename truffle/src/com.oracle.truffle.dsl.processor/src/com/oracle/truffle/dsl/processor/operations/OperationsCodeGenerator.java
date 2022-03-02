package com.oracle.truffle.dsl.processor.operations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.AnnotationProcessor;
import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.generator.CodeTypeElementFactory;
import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.ArrayCodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.DeclaredCodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;

public class OperationsCodeGenerator extends CodeTypeElementFactory<OperationsData> {

    private class OperationsCodeGeneratorImpl {

        private ProcessorContext context;
        private AnnotationProcessor<?> processor;
        private OperationsData m;

        private CodeTypeElement builderType;
        private CodeTypeElement builderImplType;
        private CodeTypeElement builderNodeType;
        private CodeTypeElement builderLabelImplType;
        private CodeTypeElement builderBytecodeNodeType;

        private CodeVariableElement argumentStackField;
        private CodeVariableElement childStackField;
        private CodeVariableElement typeStackField;

        private CodeExecutableElement doBeginOperationMethod;
        private CodeExecutableElement doEndOperationMethod;
        private CodeExecutableElement doEmitOperationMethod;

        private OperationsBytecodeCodeGenerator bytecodeBuilderGenerator;

        OperationsCodeGeneratorImpl(ProcessorContext context, AnnotationProcessor<?> processor, OperationsData m) {
            this.context = context;
            this.processor = processor;
            this.m = m;

            this.bytecodeBuilderGenerator = new OperationsBytecodeCodeGenerator(types, context);
        }

        /**
         * Creates the builder class itself. This class only contains abstract methods, the builder
         * implementation class, and the <code>createBuilder</code> method.
         *
         * @return The created builder class
         */
        CodeTypeElement createBuilder() {

            String simpleName = m.getTemplateType().getSimpleName() + "Builderino";
            builderType = GeneratorUtils.createClass(m, null, Set.of(Modifier.ABSTRACT, Modifier.PUBLIC), simpleName, types.OperationsBuilder);

            createLabelMethods(builderType, true);
            for (OperationsData.Operation op : m.getOperations()) {
                createBeginEnd(builderType, op, true);
            }

            createBuilderImpl();
            createCreateBuilder();

            return builderType;
        }

        /**
         * Create the implementation class. This class implements the begin/end methods, and in
         * general does the most of the build-time heavy lifting.
         */
        private void createBuilderImpl() {
            String simpleName = m.getTemplateType().getSimpleName() + "BuilderinoImpl";
            builderImplType = GeneratorUtils.createClass(m, null, Set.of(Modifier.PRIVATE, Modifier.STATIC), simpleName, builderType.asType());

            createBuilderNodeTypeConstants();
            createBuilderNodeOpcodeConstants();

            createBuilderImplNode();
            createBuilderLabelImpl();
            createBuilderBytecodeNode();

            childStackField = createStackField("child", generic(context.getTypeElement(ArrayList.class), builderNodeType.asType()));
            argumentStackField = createStackField("argument", new ArrayCodeTypeMirror(context.getType(Object.class)));
            typeStackField = createStackField("type", context.getType(Integer.class));

            {
                CodeExecutableElement mReset = new CodeExecutableElement(
                                Set.of(Modifier.PUBLIC),
                                context.getType(void.class), "reset");

                CodeTreeBuilder builder = mReset.getBuilder();
                builder.startStatement().string("childStack.clear()").end();
                builder.startStatement().string("childStack.add(new ArrayList<>())").end();
                builder.startStatement().string("argumentStack.clear()").end();
                builder.startStatement().string("typeStack.clear()").end();

                builderImplType.add(mReset);
            }

            createBuildMethod();

            {
                CodeExecutableElement ctor = new CodeExecutableElement(null, simpleName);

                CodeTreeBuilder builder = ctor.getBuilder();
                builder.startStatement().string("reset()").end();

                builderImplType.add(ctor);
            }

            createHelperEmitMethods();

            createLabelMethods(builderImplType, false);

            for (OperationsData.Operation op : m.getOperations()) {
                createBeginEnd(builderImplType, op, false);
            }

            builderType.add(builderImplType);
        }

        private void createBuildMethod() {
            CodeExecutableElement mBuild = new CodeExecutableElement(Set.of(Modifier.PUBLIC), types.OperationsNode, "build");

            CodeTreeBuilder builder = mBuild.getBuilder();

            builder.startAssert();
            builder.string("childStack.size() == 1");
            builder.end();

            builder.declaration(arrayOf(builderNodeType.asType()), "operations", "childStack.get(0).toArray(new " + builderNodeType.getSimpleName() + "[0])");
            builder.declaration(builderNodeType.asType(), "rootNode", "new " + builderNodeType.getSimpleName() + "(TYPE_PRIM_BLOCK, new Object[0], operations)");
            builder.declaration("byte[]", "bc", "new byte[65535]");
            builder.declaration("int", "len", "rootNode.build(bc, 0)");
            builder.declaration("byte[]", "bcCopy", "java.util.Arrays.copyOf(bc, len)");

            builder.startReturn();
            builder.startNew(builderBytecodeNodeType.asType());
            builder.string("bcCopy");
            builder.end(2);

            builderImplType.add(mBuild);

        }

        private void createBuilderLabelImpl() {
            String simpleName = builderImplType.getSimpleName() + "Label";
            builderLabelImplType = GeneratorUtils.createClass(m, null, Set.of(Modifier.PRIVATE, Modifier.STATIC), simpleName, types.OperationLabel);

            builderImplType.add(builderLabelImplType);
        }

        private void createLabelMethods(CodeTypeElement target, boolean isAbstract) {
            Set<Modifier> modifiers = isAbstract ? Set.of(Modifier.PUBLIC, Modifier.ABSTRACT) : Set.of(Modifier.PUBLIC);

            CodeExecutableElement mCreateLabel = new CodeExecutableElement(modifiers, types.OperationLabel, "createLabel");
            CodeExecutableElement mMarkLabel = new CodeExecutableElement(modifiers, context.getType(void.class), "markLabel", new CodeVariableElement(types.OperationLabel, "label"));

            target.add(mCreateLabel);
            target.add(mMarkLabel);

            if (isAbstract)
                return;
            {
                CodeTreeBuilder builder = mCreateLabel.getBuilder();
                builder.startReturn();
                builder.startNew(builderLabelImplType.asType());
                builder.end(2);
            }
        }

        /**
         * Create the BuilderNode class.
         *
         * This class represents the built method internally. Right now it only supports generating
         * the bytecode, but in the future it will support analysis and finding super-instructions.
         */
        private void createBuilderImplNode() {
            String simpleName = builderImplType.getSimpleName() + "Node";
            builderNodeType = GeneratorUtils.createClass(m, null, Set.of(Modifier.PRIVATE, Modifier.STATIC), simpleName, null);

            createBuilderNodeFields();
            builderNodeType.add(GeneratorUtils.createConstructorUsingFields(Set.of(), builderNodeType));

            createBuilderNodeBuildMethod();

            builderImplType.add(builderNodeType);
        }

        private void createBuilderNodeFields() {
            Set<Modifier> modifiers = Set.of(Modifier.PRIVATE, Modifier.FINAL);
            builderNodeType.add(new CodeVariableElement(modifiers, context.getType(int.class), "type"));
            builderNodeType.add(new CodeVariableElement(modifiers, new ArrayCodeTypeMirror(context.getType(Object.class)), "arguments"));
            builderNodeType.add(new CodeVariableElement(modifiers, new ArrayCodeTypeMirror(builderNodeType.asType()), "children"));
        }

        private void createBuilderNodeBuildMethod() {
            CodeVariableElement bc = new CodeVariableElement(arrayOf(context.getType(byte.class)), "bc");
            CodeVariableElement startBci = new CodeVariableElement(context.getType(int.class), "startBci");
            CodeExecutableElement method = new CodeExecutableElement(Set.of(), context.getType(int.class), "build", bc, startBci);
            CodeTreeBuilder builder = method.getBuilder();

            builder.declaration("int", "bci", "startBci");

            OperationsBytecodeCodeGenerator.Builder gen = bytecodeBuilderGenerator.new Builder(builder);

            builder.startSwitch();
            builder.string("this.type");
            builder.end();
            builder.startBlock();

            for (OperationsData.Operation op : m.getOperations()) {
                builder.startCase();
                builder.string(op.getTypeConstant().getName());
                builder.end();

                builder.startBlock();

                gen.buildOperation(op);

                builder.startStatement().string("break").end();
                builder.end();
            }

            builder.end();

            builder.startReturn().string("bci").end();

            builderNodeType.add(method);
        }

        /**
         * Create the node type constants.
         *
         * Instead of having a proper hierarchy of node types, we just use one BuilderNode class,
         * with this type representing which node it is.
         */
        private void createBuilderNodeTypeConstants() {
            int i = 0;
            for (OperationsData.Operation op : m.getOperations()) {
                CodeVariableElement el = new CodeVariableElement(
                                Set.of(Modifier.STATIC, Modifier.PRIVATE, Modifier.FINAL),
                                context.getType(int.class), "TYPE_" + op.getScreamCaseName());
                CodeTreeBuilder ctb = el.createInitBuilder();
                ctb.string("" + i);
                i++;

                op.setTypeConstant(el);
                builderImplType.add(el);
            }
        }

        private void createBuilderNodeOpcodeConstants() {
            int i = 0;

            {
                int numCommon = OperationsBytecodeCodeGenerator.commonOpcodeNames.length;
                CodeVariableElement[] commonOpcodes = new CodeVariableElement[numCommon];
                for (int j = 0; j < numCommon; j++) {
                    commonOpcodes[j] = createBuilderNodeOpcodeConstant(i++, OperationsBytecodeCodeGenerator.commonOpcodeNames[j]);
                }
                bytecodeBuilderGenerator.commonOpcodeConstants = commonOpcodes;
            }

            for (OperationsData.Operation op : m.getOperations()) {
                if (op.type.numOpcodes == 0)
                    continue;

                CodeVariableElement[] opcodes = new CodeVariableElement[op.type.numOpcodes];
                for (int j = 0; j < op.type.numOpcodes; j++) {
                    opcodes[j] = createBuilderNodeOpcodeConstant(i++, op.getScreamCaseName() + "_" + j);
                }

                op.setOpcodeConstant(opcodes);
            }
        }

        private CodeVariableElement createBuilderNodeOpcodeConstant(int i, String name) {
            CodeVariableElement el = new CodeVariableElement(
                            Set.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL),
                            context.getType(byte.class),
                            "OPC_" + name,
                            "" + i);
            builderImplType.add(el);
            return el;
        }

        /**
         * Create the BytecodeNode type. This type contains the bytecode interpreter, and is the
         * executable Truffle node.
         */
        private void createBuilderBytecodeNode() {
            String simpleName = builderType.getSimpleName() + "BytecodeNode";
            builderBytecodeNodeType = GeneratorUtils.createClass(m, null, Set.of(Modifier.PRIVATE, Modifier.STATIC), simpleName, types.OperationsNode);

            builderBytecodeNodeType.add(new CodeVariableElement(Set.of(Modifier.PRIVATE, Modifier.FINAL), arrayOf(context.getType(byte.class)), "bc"));
            builderBytecodeNodeType.add(GeneratorUtils.createConstructorUsingFields(Set.of(), builderBytecodeNodeType));

            createBuilderBytecodeNodeExecute();

            builderImplType.add(builderBytecodeNodeType);
        }

        private void createBuilderBytecodeNodeExecute() {
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
                CodeExecutableElement mContinueAt = new CodeExecutableElement(
                                Set.of(Modifier.PUBLIC), context.getType(Object.class), "continueAt",
                                new CodeVariableElement(types.VirtualFrame, "frame"),
                                new CodeVariableElement(types.OperationLabel, "startIndex"));

                CodeTreeBuilder builder = mContinueAt.getBuilder();

                OperationsBytecodeCodeGenerator.Executor gen = bytecodeBuilderGenerator.new Executor(builder);

                builder.declaration("final Object[]", "stack", "new Object[1024]");
                builder.declaration("final Object[]", "locals", "new Object[1024]");
                builder.declaration("int", "sp", "0");
                builder.declaration("int", "bci", "0");

                builder.statement("Object returnValue");

                builder.string("loop: ");
                builder.startWhile().string("true").end();
                builder.startBlock();

                builder.statement("int nextBci");

                builder.startSwitch().string("bc[bci]").end();
                builder.startBlock();

                gen.buildCommonOperations();

                for (OperationsData.Operation op : m.getOperations()) {
                    gen.buildOperation(op);
                }

                builder.caseDefault().startCaseBlock().tree(GeneratorUtils.createShouldNotReachHere("unknown opcode encountered")).end();

                builder.end(); // switch block

                builder.statement("bci = nextBci");
                builder.end(); // while block

                builder.startReturn().string("returnValue").end();

                builderBytecodeNodeType.add(mContinueAt);
            }

            {
                CodeExecutableElement mDump = new CodeExecutableElement(Set.of(Modifier.PUBLIC), context.getType(String.class), "dump");

                CodeTreeBuilder builder = mDump.getBuilder();

                OperationsBytecodeCodeGenerator.Dumper gen = bytecodeBuilderGenerator.new Dumper(builder);

                builder.declaration("int", "bci", "0");
                builder.declaration("StringBuilder", "sb", "new StringBuilder()");

                builder.startWhile().string("bci < bc.length").end();
                builder.startBlock(); // while block

                builder.statement("sb.append(String.format(\" %04x \", bci))");

                builder.startSwitch().string("bc[bci]").end();
                builder.startBlock();

                gen.buildCommonOperations();

                for (OperationsData.Operation op : m.getOperations()) {
                    gen.buildOperation(op);
                }

                builder.caseDefault().startCaseBlock();
                builder.statement("sb.append(String.format(\"unknown 0x%02x\", bc[bci++]))");
                builder.statement("break");
                builder.end(); // default case block
                builder.end(); // switch block

                builder.statement("sb.append(\"\\n\")");

                builder.end(); // while block

                builder.startReturn().string("sb.toString()").end();

                builderBytecodeNodeType.add(mDump);
            }
        }

        /**
         * Creates the doBeginOperation, doEndOperation and doEmitOperation helper methods. They
         * look as follows:
         *
         * <pre>
         * private void doBeginOperation(int type, Object... arguments) {
         *     typeStack.add(type);
         *     childStack.add(new ArrayList<>());
         *     argumentStack.add(arguments);
         * }
         *
         * private void doEndOperation(int type) {
         *     int topType = typeStack.pop();
         *     assert topType == type;
         *     Object[] args = argumentStack.pop();
         *     SlOperationsBuilderinoImplNode[] children = childStack.pop().toArray(new SlOperationsBuilderinoImplNode[0]);
         *     SlOperationsBuilderinoImplNode result = new SlOperationsBuilderinoImplNode(type, args, children);
         *     childStack.peek().add(result);
         * }
         *
         * private void doEmitOperation(int type, Object... arguments) {
         *     SlOperationsBuilderinoImplNode result = new SlOperationsBuilderinoImplNode(type, arguments, new SlOperationsBuilderinoImplNode[0]);
         *     childStack.peek().add(result);
         * }
         * </pre>
         */
        private void createHelperEmitMethods() {
            Set<Modifier> modPrivate = Set.of(Modifier.PRIVATE);

            {
                doBeginOperationMethod = new CodeExecutableElement(modPrivate, context.getType(void.class), "doBeginOperation");
                doBeginOperationMethod.addParameter(new CodeVariableElement(context.getType(int.class), "type"));
                doBeginOperationMethod.addParameter(new CodeVariableElement(context.getType(Object.class), "...arguments"));

                CodeTreeBuilder builder = doBeginOperationMethod.getBuilder();

                builder.startStatement();
                builder.string("typeStack.add(type)");
                builder.end();

                builder.startStatement();
                builder.string("childStack.add(new ArrayList<>())");
                builder.end();

                builder.startStatement();
                builder.string("argumentStack.add(arguments)");
                builder.end();

                builderImplType.add(doBeginOperationMethod);
            }

            {
                doEndOperationMethod = new CodeExecutableElement(modPrivate, context.getType(void.class), "doEndOperation");
                doEndOperationMethod.addParameter(new CodeVariableElement(context.getType(int.class), "type"));

                CodeTreeBuilder builder = doEndOperationMethod.getBuilder();

                builder.startStatement();
                builder.string("int topType = typeStack.pop()");
                builder.end();

                builder.startAssert();
                builder.string("topType == type");
                builder.end();

                builder.startStatement();
                builder.string("Object[] args = argumentStack.pop()");
                builder.end();

                builder.declaration(new ArrayCodeTypeMirror(builderNodeType.asType()), "children",
                                "childStack.pop().toArray(new " + builderNodeType.getSimpleName() + "[0])");

                builder.declaration(builderNodeType.asType(), "result",
                                "new " + builderNodeType.getSimpleName() + "(type, args, children)");

                builder.startStatement();
                builder.string("childStack.peek().add(result)");
                builder.end();

                builderImplType.add(doEndOperationMethod);

            }

            {
                doEmitOperationMethod = new CodeExecutableElement(modPrivate, context.getType(void.class), "doEmitOperation");
                doEmitOperationMethod.addParameter(new CodeVariableElement(context.getType(int.class), "type"));
                doEmitOperationMethod.addParameter(new CodeVariableElement(context.getType(Object.class), "...arguments"));

                CodeTreeBuilder builder = doEmitOperationMethod.getBuilder();

                builder.declaration(builderNodeType.asType(), "result",
                                "new " + builderNodeType.getSimpleName() + "(type, arguments, new " + builderNodeType.getSimpleName() + "[0])");

                builder.startStatement();
                builder.string("childStack.peek().add(result)");
                builder.end();

                builderImplType.add(doEmitOperationMethod);
            }
        }

        /**
         * Create the begin/end methods, or the emit method if the operation takes no children.
         *
         * @param target
         * @param operation
         * @param isAbstract
         */
        private void createBeginEnd(CodeTypeElement target, OperationsData.Operation operation, boolean isAbstract) {
            Set<Modifier> mods = isAbstract ? Set.of(Modifier.PUBLIC, Modifier.ABSTRACT) : Set.of(Modifier.PUBLIC);

            if (operation.children == 0) {
                CodeExecutableElement mEmit = new CodeExecutableElement(mods, context.getType(void.class), "emit" + operation.getName());

                createBeginParameters(operation, mEmit);

                target.add(mEmit);

                if (isAbstract)
                    return;

                CodeTreeBuilder builder = mEmit.getBuilder();
                builder.startStatement();
                builder.startCall(null, doEmitOperationMethod);
                builder.field(null, operation.getTypeConstant());
                for (int i = 0; i < operation.getArguments(types, context).size(); i++) {
                    builder.string("arg" + i);
                }
                builder.end(2);
            } else {
                CodeExecutableElement mBegin = new CodeExecutableElement(mods, context.getType(void.class), "begin" + operation.getName());
                CodeExecutableElement mEnd = new CodeExecutableElement(mods, context.getType(void.class), "end" + operation.getName());

                createBeginParameters(operation, mBegin);

                target.add(mBegin);
                target.add(mEnd);

                if (isAbstract)
                    return;

                {
                    CodeTreeBuilder builder = mBegin.getBuilder();
                    builder.startStatement();
                    builder.startCall(null, doBeginOperationMethod);
                    builder.field(null, operation.getTypeConstant());
                    for (int i = 0; i < operation.getArguments(types, context).size(); i++) {
                        builder.string("arg" + i);
                    }
                    builder.end(2);
                }

                {
                    CodeTreeBuilder builder = mEnd.getBuilder();
                    builder.startStatement();
                    builder.startCall(null, doEndOperationMethod);
                    builder.field(null, operation.getTypeConstant());
                    builder.end(2);
                }
            }
        }

        /**
         * Create the parameters of a begin or emit method, from given operation description.
         *
         * @param operation
         * @param method
         */
        private void createBeginParameters(OperationsData.Operation operation, CodeExecutableElement method) {
            int i = 0;
            for (TypeMirror argType : operation.getArguments(types, context)) {
                method.addParameter(new CodeVariableElement(argType, "arg" + i));
                i++;
            }
        }

        private void createCreateBuilder() {
            CodeExecutableElement method = new CodeExecutableElement(Set.of(Modifier.PUBLIC, Modifier.STATIC), builderType.asType(), "createBuilder");
            CodeTreeBuilder builder = method.getBuilder();

            builder.startReturn();
            builder.startNew(builderImplType.asType());
            builder.end();
            builder.end();

            builderType.add(method);
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

            builderImplType.add(element);
            return element;
        }

    }

    private static TypeMirror generic(TypeElement el, TypeMirror... params) {
        return new DeclaredCodeTypeMirror(el, Arrays.asList(params));
    }

    private static TypeMirror arrayOf(TypeMirror el) {
        return new ArrayCodeTypeMirror(el);
    }

    @Override
    public List<CodeTypeElement> create(ProcessorContext context, AnnotationProcessor<?> processor, OperationsData m) {
        OperationsCodeGeneratorImpl impl = new OperationsCodeGeneratorImpl(context, processor, m);
        return List.of(impl.createBuilder());
    }

}
