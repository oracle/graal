package com.oracle.truffle.dsl.processor.operations;

import static com.oracle.truffle.dsl.processor.operations.OperationGeneratorUtils.createClearStackSlot;
import static com.oracle.truffle.dsl.processor.operations.OperationGeneratorUtils.createReadLocal;
import static com.oracle.truffle.dsl.processor.operations.OperationGeneratorUtils.createReadStack;
import static com.oracle.truffle.dsl.processor.operations.OperationGeneratorUtils.createWriteLocal;
import static com.oracle.truffle.dsl.processor.operations.OperationGeneratorUtils.createWriteStackObject;

import java.util.List;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.ElementFilter;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.generator.NodeCodeGenerator;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeNames;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.operations.Operation.BuilderVariables;
import com.oracle.truffle.dsl.processor.operations.SingleOperationData.ParameterKind;

public abstract class Instruction {

    public final String name;
    public final int id;
    public final Argument[] arguments;

    public CodeVariableElement opcodeIdField;

    public static class ExecuteVariables {
        CodeTypeElement bytecodeNodeType;

        CodeVariableElement bc;
        CodeVariableElement bci;
        CodeVariableElement nextBci;
        CodeVariableElement returnValue;
        CodeVariableElement frame;
        CodeVariableElement sp;

        CodeVariableElement consts;
        CodeVariableElement maxStack;
        CodeVariableElement handlers;

        CodeVariableElement tracer;
    }

    public Instruction(String name, int id, Argument... arguments) {
        this.name = name;
        this.id = id;
        this.arguments = arguments;
    }

    public void setOpcodeIdField(CodeVariableElement opcodeIdField) {
        this.opcodeIdField = opcodeIdField;
    }

    public int length() {
        int len = 1;
        for (Argument arg : getArgumentTypes()) {
            len += arg.length;
        }
        return len;
    }

    public List<Argument> getArgumentTypes() {
        return List.of(arguments);
    }

    public abstract CodeTree createPushCountCode(BuilderVariables vars);

    protected abstract CodeTree createStackEffect(BuilderVariables vars, CodeTree[] arguments2);

    public abstract CodeTree createExecuteCode(ExecuteVariables vars);

    public CodeTree createExecuteEpilogue(ExecuteVariables vars) {

        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        if (isNormalControlFlow()) {
            b.startAssign(vars.nextBci).variable(vars.bci).string(" + " + length()).end();
        }

        b.statement("break");

        return b.build();
    }

    public CodeTree createBreakCode(ExecuteVariables vars) {
        return CodeTreeBuilder.createBuilder().statement("break").build();
    }

    public boolean isNormalControlFlow() {
        return true;
    }

    public CodeTree createBuildCode(BuilderVariables vars, CodeTree[] argValues) {

        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        b.startStatement();
        b.variable(vars.bc).string("[").variable(vars.bci).string("++]");
        b.string(" = ");
        b.variable(opcodeIdField);
        b.end();

        assert argValues.length == arguments.length;
        for (int i = 0; i < arguments.length; i++) {
            b.tree(arguments[i].createBuildCode(vars, argValues[i]));
            b.startAssign(vars.bci).variable(vars.bci).string(" + " + arguments[i].length).end();
        }

        return b.build();
    }

    abstract static class SimpleInstruction extends Instruction {

        private final int pushCount;
        private final int popCount;

        public SimpleInstruction(String name, int id, int pushCount, int popCount, Argument... arguments) {
            super(name, id, arguments);
            this.pushCount = pushCount;
            this.popCount = popCount;
        }

        @Override
        public CodeTree createPushCountCode(BuilderVariables vars) {
            return CodeTreeBuilder.singleString("" + pushCount);
        }

        @Override
        public CodeTree createExecuteEpilogue(ExecuteVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            for (int i = 0; i < (popCount - pushCount); i++) {
                createClearStackSlot(vars, i);
            }

            b.startAssign(vars.sp).variable(vars.sp).string(" + " + (pushCount - popCount)).end();
            b.tree(super.createExecuteEpilogue(vars));
            return b.build();
        }
    }

    public static class Pop extends SimpleInstruction {
        public Pop(int id) {
            super("pop", id, 0, 1);
        }

        @Override
        public CodeTree createExecuteCode(ExecuteVariables vars) {
            return null;
        }

        @Override
        protected CodeTree createStackEffect(BuilderVariables vars, CodeTree[] arguments2) {
            return CodeTreeBuilder.singleString("-1");
        }
    }

    public static class BranchFalse extends SimpleInstruction {
        public BranchFalse(int id) {
            super("br.false", id, 0, 1, new Argument.BranchTarget());
        }

        @Override
        public CodeTree createExecuteCode(ExecuteVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.declaration("Object", "condition", createReadStack(vars, 0));
            b.startIf().string("(boolean) condition").end();
            b.startBlock();
            b.startAssign(vars.nextBci).variable(vars.bci).string(" + " + length()).end();
            b.end().startElseBlock();
            b.startAssign(vars.nextBci).tree(arguments[0].createReadCode(vars, 1)).end();
            b.end();

            return b.build();
        }

        @Override
        public boolean isNormalControlFlow() {
            return false;
        }

        @Override
        protected CodeTree createStackEffect(BuilderVariables vars, CodeTree[] arguments2) {
            return CodeTreeBuilder.singleString("-1");
        }
    }

    public static class Branch extends SimpleInstruction {
        public Branch(int id) {
            super("br", id, 0, 0, new Argument.BranchTarget());
        }

        @Override
        public CodeTree createExecuteCode(ExecuteVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.startAssign(vars.nextBci).tree(arguments[0].createReadCode(vars, 1)).end();

            b.startIf().variable(vars.nextBci).string(" <= ").variable(vars.bci).end();
            b.startBlock();
            b.startStatement().startStaticCall(ProcessorContext.getInstance().getDeclaredType("com.oracle.truffle.api.TruffleSafepoint"), "poll")//
                            .string("this")//
                            .end(2);
            b.end();

            return b.build();
        }

        @Override
        public boolean isNormalControlFlow() {
            return false;
        }

        @Override
        protected CodeTree createStackEffect(BuilderVariables vars, CodeTree[] arguments2) {
            return CodeTreeBuilder.singleString("0");
        }
    }

    public static class ConstObject extends SimpleInstruction {
        public ConstObject(int id) {
            super("const", id, 1, 0, new Argument.Const());
        }

        @Override
        public CodeTree createExecuteCode(ExecuteVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.tree(createWriteStackObject(vars, 1, arguments[0].createReadCode(vars, 1)));

            return b.build();
        }

        @Override
        protected CodeTree createStackEffect(BuilderVariables vars, CodeTree[] arguments2) {
            return CodeTreeBuilder.singleString("1");
        }
    }

    public static class Return extends SimpleInstruction {
        public Return(int id) {
            super("ret", id, 0, 1);
        }

        @Override
        public CodeTree createExecuteCode(ExecuteVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.startAssign(vars.returnValue).tree(createReadStack(vars, 0)).end();

            return b.build();
        }

        @Override
        public CodeTree createExecuteEpilogue(ExecuteVariables vars) {
            return CodeTreeBuilder.createBuilder().statement("break loop").build();
        }

        @Override
        public boolean isNormalControlFlow() {
            return false;
        }

        @Override
        protected CodeTree createStackEffect(BuilderVariables vars, CodeTree[] arguments2) {
            return CodeTreeBuilder.singleString("0");
        }
    }

    public static class LoadArgument extends SimpleInstruction {
        public LoadArgument(int id) {
            super("ldarg", id, 1, 0, new Argument.IntegerArgument(2));
        }

        @Override
        public CodeTree createExecuteCode(ExecuteVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.declaration("int", "index", arguments[0].createReadCode(vars, 1));

            CodeTree val = CodeTreeBuilder.createBuilder()//
                            .startCall(vars.frame, "getArguments").end()//
                            .string("[index]").build();

            b.tree(createWriteStackObject(vars, 1, val));

            return b.build();
        }

        @Override
        protected CodeTree createStackEffect(BuilderVariables vars, CodeTree[] arguments2) {
            return CodeTreeBuilder.singleString("1");
        }
    }

    public static class LoadLocal extends SimpleInstruction {
        public LoadLocal(int id) {
            super("ldloc", id, 1, 0, new Argument.IntegerArgument(2));
        }

        @Override
        public CodeTree createExecuteCode(ExecuteVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.declaration("int", "index", arguments[0].createReadCode(vars, 1));

            CodeTree val = createReadLocal(vars, CodeTreeBuilder.singleString("index"));

            b.tree(createWriteStackObject(vars, 1, val));

            return b.build();
        }

        @Override
        protected CodeTree createStackEffect(BuilderVariables vars, CodeTree[] arguments2) {
            return CodeTreeBuilder.singleString("1");
        }
    }

    public static class StoreLocal extends SimpleInstruction {
        public StoreLocal(int id) {
            super("stloc", id, 0, 1, new Argument.IntegerArgument(2));
        }

        @Override
        public CodeTree createExecuteCode(ExecuteVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.declaration("int", "index", arguments[0].createReadCode(vars, 1));

            CodeTree val = createReadStack(vars, 0);

            b.tree(createWriteLocal(vars, CodeTreeBuilder.singleString("index"), val));

            return b.build();
        }

        @Override
        protected CodeTree createStackEffect(BuilderVariables vars, CodeTree[] arguments2) {
            return CodeTreeBuilder.singleString("-1");
        }

        @Override
        public CodeTree createBuildCode(BuilderVariables vars, CodeTree[] argValues) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.startIf().variable(vars.maxLocal).string(" < ").tree(argValues[0]).end();
            b.startAssign(vars.maxLocal).tree(argValues[0]).end();
            b.tree(super.createBuildCode(vars, argValues));

            return b.build();
        }
    }

    public static class Custom extends Instruction {

        public final SingleOperationData data;

        public final int stackPops;
        public final int stackPushes;
        public final boolean isVarArgs;

        public Custom(String name, int id, SingleOperationData data, Argument... arguments) {
            super(name, id, arguments);
            this.data = data;
            this.stackPops = data.getMainProperties().numStackValues;
            this.isVarArgs = data.getMainProperties().isVariadic;
            this.stackPushes = data.getMainProperties().returnsValue ? 1 : 0;

            if (data.getMainProperties().isVariadic && arguments.length == 0)
                throw new IllegalArgumentException("Must have at least the VarArgCount argument");
        }

        @Override
        public CodeTree createPushCountCode(BuilderVariables vars) {
            return CodeTreeBuilder.singleString("" + stackPushes);
        }

        @Override
        public CodeTree createExecuteEpilogue(ExecuteVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            if (this.isVarArgs) {
                b.startFor().string("int i = 0; i < varArgCount; i++").end();
                b.startBlock();
                b.tree(createClearStackSlot(vars, "-i - 1"));
                b.end();

                for (int i = 0; i < stackPops - 1; i++) {
                    b.tree(createClearStackSlot(vars, "-varArgCount - " + i));
                }

                b.startAssign(vars.sp).variable(vars.sp).string(" + " + (stackPushes - stackPops + 1) + " - varArgCount").end();
            } else {
                for (int i = 0; i < stackPops - 1; i++) {
                    b.tree(createClearStackSlot(vars, i));
                }

                b.startAssign(vars.sp).variable(vars.sp).string(" + " + (stackPushes - stackPops)).end();
            }

            b.tree(super.createExecuteEpilogue(vars));

            return b.build();
        }

        @Override
        public CodeTree createExecuteCode(ExecuteVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            CodeTree[] vals = new CodeTree[stackPops];
            String destIndex;
            if (isVarArgs) {
                b.declaration("byte", "varArgCount", arguments[0].createReadCode(vars, 1));
                b.declaration("Object[]", "varArgs", "new Object[varArgCount]");

                b.startFor().string("int i = 0; i < varArgCount; i++").end();
                b.startBlock();

                String stackIndex = "i - varArgCount + 1";

                b.startStatement();
                b.string("varArgs[i] = ");
                b.tree(createReadStack(vars, CodeTreeBuilder.singleString(stackIndex)));
                b.end();

                b.end();

                vals[stackPops - 1] = CodeTreeBuilder.singleString("varArgs");

                for (int i = 1; i < stackPops; i++) {
                    String stackIndex2 = "- (varArgCount + " + i + " - 1)";
                    vals[vals.length - 1 - i] = createReadStack(vars, CodeTreeBuilder.singleString(stackIndex2));
                }

                destIndex = (2 - stackPops) + " - varArgCount";

            } else {
                for (int i = 0; i < stackPops; i++) {
                    vals[vals.length - 1 - i] = createReadStack(vars, -i);
                }

                destIndex = "" + (1 - stackPops);
            }

            if (stackPushes > 0) {
                b.declaration("Object", "result", createActualExecuteCallCode(vars, vals));
// b.statement("System.out.println(\" " + name + " result: \" + result)");
                b.tree(createWriteStackObject(vars, destIndex, CodeTreeBuilder.singleString("result")));
            } else {
                b.statement(createActualExecuteCallCode(vars, vals));
            }

            return b.build();
        }

        private CodeTree createActualExecuteCallCode(ExecuteVariables vars, CodeTree[] vals) {
            String executeName = "execute" + data.getTemplateType().getSimpleName() + "_";

            CodeTypeElement topElem = new NodeCodeGenerator().create(data.getContext(), null, data.getNodeData()).get(0);

            TypeElement typUncached = null;
            ExecutableElement metExecute = null;

            outer: for (TypeElement elem : ElementFilter.typesIn(topElem.getEnclosedElements())) {
                if (elem.getSimpleName().toString().equals("Uncached")) {
                    typUncached = elem;
                    for (ExecutableElement exElem : ElementFilter.methodsIn(elem.getEnclosedElements())) {
                        if (exElem.getSimpleName().toString().equals("execute")) {
                            metExecute = exElem;
                            break outer;
                        }
                    }
                }
            }

            if (metExecute == null) {
                data.addError("Generated node did not have a proper execute element, what for?");
                return CodeTreeBuilder.singleString(topElem.toString());
            }

            CodeExecutableElement copy = CodeExecutableElement.clone(metExecute);
            copy.setSimpleName(CodeNames.of(executeName));
            GeneratorUtils.addSuppressWarnings(ProcessorContext.getInstance(), copy, "static-method");
            copy.getParameters().add(0, new CodeVariableElement(new CodeTypeMirror(TypeKind.INT), "$bci"));

            copy.getAnnotationMirrors().removeIf(x -> x.getAnnotationType().equals(ProcessorContext.getInstance().getTypes().CompilerDirectives_TruffleBoundary));

            vars.bytecodeNodeType.add(copy);

            for (VariableElement elem : ElementFilter.fieldsIn(topElem.getEnclosedElements())) {
                if (elem.getModifiers().contains(Modifier.STATIC) && !elem.getSimpleName().toString().equals("UNCACHED")) {
                    CodeVariableElement fldCopy = CodeVariableElement.clone(elem);
                    fldCopy.setSimpleName(CodeNames.of(data.getName() + "_" + elem.getSimpleName()));
                    fldCopy.setInit(((CodeVariableElement) elem).getInit());
                    // fldCopy.getModifiers().remove(Modifier.STATIC);

                    OperationGeneratorUtils.changeAllVariables(copy.getBodyTree(), elem, fldCopy);

                    vars.bytecodeNodeType.add(fldCopy);
                }
            }

            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.startCall("this", copy);

            b.variable(vars.bci);

            int valIdx = 0;

            for (ParameterKind param : data.getMainProperties().parameters) {
                switch (param) {
                    case STACK_VALUE:
                    case VARIADIC:
                        b.tree(vals[valIdx++]);
                        break;
                    case VIRTUAL_FRAME:
                        b.variable(vars.frame);
                        break;
                    default:
                        throw new UnsupportedOperationException("" + param);
                }
            }

            b.end();

            return b.build();
        }

        @Override
        protected CodeTree createStackEffect(BuilderVariables vars, CodeTree[] arguments2) {
            if (this.isVarArgs) {
                return CodeTreeBuilder.singleString("(" + this.stackPushes + " - " + vars.numChildren.getName() + ")");
            } else {
                return CodeTreeBuilder.singleString(this.stackPushes - this.stackPops + "");
            }
        }

    }

}
