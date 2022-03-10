package com.oracle.truffle.dsl.processor.operations;

import static com.oracle.truffle.dsl.processor.operations.OperationGeneratorUtils.*;

import java.util.List;

import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.operations.Operation.BuilderVariables;

public abstract class Instruction {

    public final String name;
    public final int id;
    public final Argument[] arguments;

    public static class ExecuteVariables {
        CodeVariableElement bc;
        CodeVariableElement bci;
        CodeVariableElement nextBci;
        CodeVariableElement returnValue;
        CodeVariableElement frame;
        CodeVariableElement sp;

        CodeVariableElement consts;
        CodeVariableElement maxStack;
        CodeVariableElement handlers;
    }

    public Instruction(String name, int id, Argument... arguments) {
        this.name = name;
        this.id = id;
        this.arguments = arguments;
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

    protected abstract CodeTree createStackEffect(BuilderVariables vars, CodeVariableElement[] arguments2);

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

    public CodeTree createBuildCode(BuilderVariables vars, CodeVariableElement[] argValues) {

        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        b.startStatement();
        b.variable(vars.bc).string("[").variable(vars.bci).string("++]");
        b.string(" = ");
        b.string("" + id + " /* " + name + " */");
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
        protected CodeTree createStackEffect(BuilderVariables vars, CodeVariableElement[] arguments2) {
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
        protected CodeTree createStackEffect(BuilderVariables vars, CodeVariableElement[] arguments2) {
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

            return b.build();
        }

        @Override
        public boolean isNormalControlFlow() {
            return false;
        }

        @Override
        protected CodeTree createStackEffect(BuilderVariables vars, CodeVariableElement[] arguments2) {
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
        protected CodeTree createStackEffect(BuilderVariables vars, CodeVariableElement[] arguments2) {
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
        protected CodeTree createStackEffect(BuilderVariables vars, CodeVariableElement[] arguments2) {
            return CodeTreeBuilder.singleString("0");
        }
    }

    public static class LoadArgument extends SimpleInstruction {
        public LoadArgument(int id) {
            super("ldarg", id, 1, 0, new Argument.Integer(2));
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
        protected CodeTree createStackEffect(BuilderVariables vars, CodeVariableElement[] arguments2) {
            return CodeTreeBuilder.singleString("1");
        }
    }

    public static class LoadLocal extends SimpleInstruction {
        public LoadLocal(int id) {
            super("ldloc", id, 1, 0, new Argument.Integer(2));
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
        protected CodeTree createStackEffect(BuilderVariables vars, CodeVariableElement[] arguments2) {
            return CodeTreeBuilder.singleString("1");
        }
    }

    public static class StoreLocal extends SimpleInstruction {
        public StoreLocal(int id) {
            super("starg", id, 0, 1, new Argument.Integer(2));
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
        protected CodeTree createStackEffect(BuilderVariables vars, CodeVariableElement[] arguments2) {
            return CodeTreeBuilder.singleString("-1");
        }

        @Override
        public CodeTree createBuildCode(BuilderVariables vars, CodeVariableElement[] argValues) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.startIf().variable(vars.maxLocal).string(" < ").variable(argValues[0]).end();
            b.startAssign(vars.maxLocal).variable(argValues[0]).end();
            b.tree(super.createBuildCode(vars, argValues));

            return b.build();
        }
    }

    public static class Custom extends Instruction {

        public final int stackPops;
        public final int stackPushes;
        public final boolean isVarArgs;
        public final TypeElement type;

        private CodeVariableElement uncachedInstance;

        public Custom(String name, int id, int stackPops, boolean isVarArgs, boolean isVoid, TypeElement type, Argument... arguments) {
            super(name, id, arguments);
            this.stackPops = stackPops;
            this.isVarArgs = isVarArgs;
            this.stackPushes = isVoid ? 0 : 1;
            this.type = type;
        }

        @Override
        public int length() {
            return super.length() + (isVarArgs ? 1 : 0);
        }

        @Override
        public CodeTree createPushCountCode(BuilderVariables vars) {
            return CodeTreeBuilder.singleString("" + stackPushes);
        }

        public void setUncachedInstance(CodeVariableElement uncachedInstance) {
            this.uncachedInstance = uncachedInstance;
        }

        @Override
        public CodeTree createExecuteEpilogue(ExecuteVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            for (int i = 0; i < stackPops - 1; i++) {
                createClearStackSlot(vars, i);
            }

            b.startAssign(vars.sp).variable(vars.sp).string(" + " + (stackPushes - stackPops)).end();

            b.tree(super.createExecuteEpilogue(vars));

            return b.build();
        }

        @Override
        public CodeTree createExecuteCode(ExecuteVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            CodeTree[] vals = new CodeTree[stackPops];
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
                    String stackIndex2 = "- (varArgCount + " + i + ")";
                    vals[vals.length - 1 - i] = createReadStack(vars, CodeTreeBuilder.singleString(stackIndex2));
                }

            } else {
                for (int i = 0; i < stackPops; i++) {
                    vals[vals.length - 1 - i] = createReadStack(vars, -i);
                }
            }

            int resultOffset = 1 - stackPops;

            CodeTree instance = CodeTreeBuilder.createBuilder() //
                            .staticReference(uncachedInstance) //
                            .build();

            CodeTreeBuilder bCall = CodeTreeBuilder.createBuilder();
            bCall.startCall(instance, "execute");
            for (int i = 0; i < stackPops; i++) {
                bCall.tree(vals[i]);
            }
            bCall.end(2);

            if (stackPushes > 0) {
                b.tree(createWriteStackObject(vars, resultOffset, bCall.build()));
            } else {
                b.statement(bCall.build());
            }

            return b.build();
        }

        @Override
        protected CodeTree createStackEffect(BuilderVariables vars, CodeVariableElement[] arguments2) {
            if (this.isVarArgs) {
                return CodeTreeBuilder.singleString("(" + this.stackPushes + " - " + vars.numChildren.getName() + ")");
            } else {
                return CodeTreeBuilder.singleString(this.stackPushes - this.stackPops + "");
            }
        }

    }

}
