package com.oracle.truffle.dsl.processor.operations;

import static com.oracle.truffle.dsl.processor.operations.OperationGeneratorUtils.createCreateLabel;
import static com.oracle.truffle.dsl.processor.operations.OperationGeneratorUtils.createEmitInstruction;
import static com.oracle.truffle.dsl.processor.operations.OperationGeneratorUtils.createEmitLabel;
import static com.oracle.truffle.dsl.processor.operations.OperationGeneratorUtils.getTypes;

import java.util.Collection;
import java.util.List;
import java.util.Stack;

import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.operations.Instruction.ExecuteVariables;

public abstract class Operation {
    public static final int VARIABLE_CHILDREN = -1;

    public final OperationsContext builder;
    public final String name;
    public final int id;
    public final int children;

    public CodeVariableElement idConstantField;

    protected Operation(OperationsContext builder, String name, int id, int children) {
        this.builder = builder;
        this.name = name;
        this.id = id;
        this.children = children;
    }

    public final boolean isVariableChildren() {
        return children == VARIABLE_CHILDREN;
    }

    public void setIdConstantField(CodeVariableElement idConstantField) {
        this.idConstantField = idConstantField;
    }

    public static class BuilderVariables {
        CodeVariableElement bc;
        CodeVariableElement bci;
        CodeVariableElement consts;
        CodeVariableElement exteptionHandlers;

        CodeVariableElement stackUtility;

        CodeVariableElement lastChildPushCount;
        CodeVariableElement childIndex;
        CodeVariableElement numChildren;
        CodeVariableElement[] arguments;

        CodeVariableElement curStack;
        CodeVariableElement maxStack;
        CodeVariableElement maxLocal;
    }

    public int minimumChildren() {
        assert isVariableChildren() : "should only be called for variadics";
        return 0;
    }

    public List<Argument> getArguments() {
        return List.of();
    }

    public final List<TypeMirror> getBuilderArgumentTypes() {
        return getArguments().stream().map(x -> x.toBuilderArgumentType()).toList();
    }

    public CodeTree createBeginCode(BuilderVariables vars) {
        return null;
    }

    public CodeTree createAfterChildCode(BuilderVariables vars) {
        return null;
    }

    public CodeTree createBeforeChildCode(BuilderVariables vars) {
        return null;
    }

    public CodeTree createEndCode(BuilderVariables vars) {
        return null;
    }

    public CodeTree createLeaveCode(BuilderVariables vars) {
        return null;
    }

    public abstract CodeTree createPushCountCode(BuilderVariables vars);

    public static class Custom extends Operation {
        final Instruction.Custom instruction;

        protected Custom(OperationsContext builder, String name, int id, int children, Instruction.Custom instruction) {
            super(builder, name, id, instruction.isVarArgs ? VARIABLE_CHILDREN : children);
            this.instruction = instruction;
        }

        @Override
        public CodeTree createPushCountCode(BuilderVariables vars) {
            return instruction.createPushCountCode(vars);
        }

        @Override
        public List<Argument> getArguments() {
            return instruction.getArgumentTypes();
        }

        @Override
        public CodeTree createEndCode(BuilderVariables vars) {
            return createEmitInstruction(vars, instruction, vars.arguments);
        }

        @Override
        public int minimumChildren() {
            if (instruction.isVarArgs) {
                return instruction.stackPops - 1;
            } else {
                return super.minimumChildren();
            }
        }
    }

    public static class Simple extends Operation {

        private final Instruction instruction;

        protected Simple(OperationsContext builder, String name, int id, int children, Instruction instruction) {
            super(builder, name, id, children);
            this.instruction = instruction;
        }

        @Override
        public CodeTree createEndCode(BuilderVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.tree(createEmitInstruction(vars, instruction, vars.arguments));

            return b.build();
        }

        @Override
        public CodeTree createPushCountCode(BuilderVariables vars) {
            return this.instruction.createPushCountCode(vars);
        }

        @Override
        public List<Argument> getArguments() {
            return instruction.getArgumentTypes();
        }
    }

    public static class Block extends Operation {
        protected Block(OperationsContext builder, int id) {
            super(builder, "Block", id, VARIABLE_CHILDREN);
        }

        // for child classes
        protected Block(OperationsContext builder, String name, int id) {
            super(builder, name, id, VARIABLE_CHILDREN);
        }

        @Override
        public CodeTree createBeforeChildCode(BuilderVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.startIf().variable(vars.childIndex).string(" != 0").end();
            b.startBlock(); // {

            b.tree(createPopLastChildCode(vars));

            b.end(); // }

            return b.build();
        }

        @Override
        public CodeTree createPushCountCode(BuilderVariables vars) {
            return null; // does not change it at all
        }
    }

    public static class IfThen extends Operation {
        protected IfThen(OperationsContext builder, int id) {
            super(builder, "IfThen", id, 2);
        }

        @Override
        public CodeTree createAfterChildCode(BuilderVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.startIf().variable(vars.childIndex).string(" == 0").end();
            b.startBlock();
            {
                b.startAssert().variable(vars.lastChildPushCount).string(" == 1").end();

                CodeVariableElement varEndLabel = new CodeVariableElement(getTypes().BuilderOperationLabel, "endLabel");
                b.declaration(getTypes().BuilderOperationLabel, varEndLabel.getName(), createCreateLabel());

                // utilstack: ...
                b.tree(createPushUtility(varEndLabel, vars));
                // utilstack ..., endLabel

                b.tree(createEmitInstruction(vars, builder.commonBranchFalse, varEndLabel));
            }
            b.end().startElseBlock();
            {
                b.tree(createPopLastChildCode(vars));

                CodeVariableElement varEndLabel = new CodeVariableElement(getTypes().BuilderOperationLabel, "endLabel");

                // utilstack ..., endLabel
                b.tree(createPopUtility(varEndLabel, vars));
                // utilstack ...

                b.tree(createEmitLabel(vars, varEndLabel));
            }
            b.end();

            return b.build();
        }

        @Override
        public CodeTree createPushCountCode(BuilderVariables vars) {
            return CodeTreeBuilder.singleString("0");
        }
    }

    public static class IfThenElse extends Operation {

        private final boolean hasValue;

        public IfThenElse(OperationsContext builder, int id, boolean hasValue) {
            super(builder, hasValue ? "Conditional" : "IfThenElse", id, 3);
            this.hasValue = hasValue;
        }

        @Override
        public CodeTree createPushCountCode(BuilderVariables vars) {
            return CodeTreeBuilder.singleString(hasValue ? "1" : "0");
        }

        @Override
        public CodeTree createAfterChildCode(BuilderVariables vars) {

            // <<child0>>
            // brfalse elseLabel

            // <<child1>>
            // br endLabel
            // elseLabel:

            // <<child2>>
            // endLabel:

            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.startIf().variable(vars.childIndex).string(" == 0").end();
            b.startBlock();
            {
                b.startAssert().variable(vars.lastChildPushCount).string(" == 1").end();

                CodeVariableElement varElseLabel = new CodeVariableElement(getTypes().BuilderOperationLabel, "elseLabel");
                b.declaration(getTypes().BuilderOperationLabel, varElseLabel.getName(), createCreateLabel());

                // utilstack: ...
                b.tree(createPushUtility(varElseLabel, vars));
                // utilstack ..., elseLabel

                b.tree(createEmitInstruction(vars, builder.commonBranchFalse, varElseLabel));
            }
            b.end();
            b.startElseIf().variable(vars.childIndex).string(" == 1").end();
            b.startBlock(); // {
            {
                CodeVariableElement varEndLabel = new CodeVariableElement(getTypes().BuilderOperationLabel, "endLabel");
                CodeVariableElement varElseLabel = new CodeVariableElement(getTypes().BuilderOperationLabel, "elseLabel");

                if (hasValue) {
                    b.startAssert().variable(vars.lastChildPushCount).string(" == 1").end();
                } else {
                    b.tree(createPopLastChildCode(vars));
                }

                b.declaration(getTypes().BuilderOperationLabel, varEndLabel.getName(), createCreateLabel());

                // utilstack ..., elseLabel
                b.tree(createPopUtility(varElseLabel, vars));
                // utilstack ...
                b.tree(createPushUtility(varEndLabel, vars));
                // utilstack ..., endLabel

                b.tree(createEmitInstruction(vars, builder.commonBranch, varEndLabel));
                b.tree(createEmitLabel(vars, varElseLabel));
            }
            b.end().startElseBlock();
            {

                if (hasValue) {
                    b.startAssert().variable(vars.lastChildPushCount).string(" == 1").end();
                } else {
                    b.tree(createPopLastChildCode(vars));
                }

                CodeVariableElement varEndLabel = new CodeVariableElement(getTypes().BuilderOperationLabel, "endLabel");

                // utilstack ..., endLabel
                b.tree(createPopUtility(varEndLabel, vars));
                // utilstack ...

                b.tree(createEmitLabel(vars, varEndLabel));
            }
            b.end();

            return b.build();
        }
    }

    public static class While extends Operation {
        public While(OperationsContext builder, int id) {
            super(builder, "While", id, 2);
        }

        @Override
        public CodeTree createBeginCode(BuilderVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            CodeVariableElement varStartLabel = new CodeVariableElement(getTypes().BuilderOperationLabel, "startLabel");
            b.declaration(getTypes().BuilderOperationLabel, varStartLabel.getName(), createCreateLabel());

            b.tree(createEmitLabel(vars, varStartLabel));

            b.tree(createPushUtility(varStartLabel, vars));
            // utilstack: ..., startLabel

            return b.build();
        }

        @Override
        public CodeTree createAfterChildCode(BuilderVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.startIf().variable(vars.childIndex).string(" == 0").end();
            b.startBlock();
            {
                CodeVariableElement varEndLabel = new CodeVariableElement(getTypes().BuilderOperationLabel, "endLabel");

                b.startAssert().variable(vars.lastChildPushCount).string(" == 1").end();
                b.declaration(getTypes().BuilderOperationLabel, varEndLabel.getName(), createCreateLabel());

                // utilstack: ..., startLabel
                b.tree(createPushUtility(varEndLabel, vars));
                // utilstack ..., startLabel, endLabel

                b.tree(createEmitInstruction(vars, builder.commonBranchFalse, varEndLabel));
            }
            b.end().startElseBlock();
            {
                b.tree(createPopLastChildCode(vars));

                CodeVariableElement varEndLabel = new CodeVariableElement(getTypes().BuilderOperationLabel, "endLabel");
                CodeVariableElement varStartLabel = new CodeVariableElement(getTypes().BuilderOperationLabel, "startLabel");

                // utilstack ..., startLabel, endLabel
                b.tree(createPopUtility(varEndLabel, vars));
                b.tree(createPopUtility(varStartLabel, vars));
                // utilstack ...

                b.tree(createEmitInstruction(vars, builder.commonBranch, varStartLabel));

                b.tree(createEmitLabel(vars, varEndLabel));
            }

            b.end();

            return b.build();
        }

        @Override
        public CodeTree createPushCountCode(BuilderVariables vars) {
            return CodeTreeBuilder.singleString("0");
        }
    }

    public static class Label extends Operation {
        public Label(OperationsContext builder, int id) {
            super(builder, "Label", id, 0);
        }

        @Override
        public CodeTree createPushCountCode(BuilderVariables vars) {
            return CodeTreeBuilder.singleString("0");
        }

        @Override
        public CodeTree createEndCode(BuilderVariables vars) {
            return createEmitLabel(vars, CodeTreeBuilder.singleString("((BuilderOperationLabel) " + vars.arguments[0].getName() + ")"));
        }

        @Override
        public List<Argument> getArguments() {
            return List.of(new Argument.BranchTarget());
        }
    }

    public static class TryCatch extends Operation {
        public TryCatch(OperationsContext builder, int id) {
            super(builder, "TryCatch", id, 2);
        }

        @Override
        public CodeTree createPushCountCode(BuilderVariables vars) {
            return CodeTreeBuilder.singleString("0");
        }

        @Override
        public List<Argument> getArguments() {
            return List.of(new Argument.IntegerArgument(2));
        }

        @Override
        public CodeTree createBeginCode(BuilderVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            CodeVariableElement varBeh = new CodeVariableElement(getTypes().BuilderExceptionHandler, "beh");
            b.declaration(getTypes().BuilderExceptionHandler, "beh", CodeTreeBuilder.createBuilder().startNew(getTypes().BuilderExceptionHandler).end().build());
            b.startStatement().variable(varBeh).string(".startBci = ").variable(vars.bci).end();
            b.startStatement().variable(varBeh).string(".startStack = ").variable(vars.curStack).end();
            b.startStatement().variable(varBeh).string(".exceptionIndex = ").variable(vars.arguments[0]).end();
            b.startStatement().startCall(vars.exteptionHandlers, "add").variable(varBeh).end(2);

            // ...
            b.tree(createPushUtility(varBeh, vars));
            // ..., beh

            CodeVariableElement varEndLabel = new CodeVariableElement(getTypes().BuilderOperationLabel, "endLabel");
            b.declaration(getTypes().BuilderOperationLabel, varEndLabel.getName(), createCreateLabel());

            // utilstack: ..., beh
            b.tree(createPushUtility(varEndLabel, vars));
            // utilstack ..., beh, endLabel

            return b.build();
        }

        @Override
        public CodeTree createAfterChildCode(BuilderVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.tree(createPopLastChildCode(vars));

            b.startIf().variable(vars.childIndex).string(" == 0").end();
            b.startBlock();

            CodeVariableElement varEndLabel = new CodeVariableElement(getTypes().BuilderOperationLabel, "endLabel");
            CodeVariableElement varBeh = new CodeVariableElement(getTypes().BuilderExceptionHandler, "beh");

            // utilstack ..., beh, endLabel
            b.tree(createPeekUtility(varEndLabel, vars, 0));
            b.tree(createPeekUtility(varBeh, vars, 1));

            b.startStatement().variable(varBeh).string(".endBci = ").variable(vars.bci).end();

            b.tree(createEmitInstruction(vars, builder.commonBranch, varEndLabel));

            b.end().startElseBlock();

            b.end();

            return b.build();
        }

        @Override
        public CodeTree createBeforeChildCode(BuilderVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.startIf().variable(vars.childIndex).string(" == 1").end();
            b.startBlock();

            CodeVariableElement varEndLabel = new CodeVariableElement(getTypes().BuilderOperationLabel, "endLabel");
            CodeVariableElement varBeh = new CodeVariableElement(getTypes().BuilderExceptionHandler, "beh");

            // utilstack ..., beh, endLabel
            b.tree(createPeekUtility(varEndLabel, vars, 0));
            b.tree(createPeekUtility(varBeh, vars, 1));

            b.startAssign(vars.curStack).variable(varBeh).string(".startStack").end();
            b.startStatement().variable(varBeh).string(".handlerBci = ").variable(vars.bci).end();

            b.end();

            return b.build();
        }

        @Override
        public CodeTree createEndCode(BuilderVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
            CodeVariableElement varEndLabel = new CodeVariableElement(getTypes().BuilderOperationLabel, "endLabel");
            CodeVariableElement varBeh = new CodeVariableElement(getTypes().BuilderExceptionHandler, "beh");
            b.tree(createPopUtility(varEndLabel, vars));
            b.tree(createPopUtility(varBeh, vars));

            b.tree(createEmitLabel(vars, CodeTreeBuilder.singleVariable(varEndLabel)));

            return b.build();
        }
    }

    public static class Instrumentation extends Block {
        public Instrumentation(OperationsContext builder, int id) {
            super(builder, "Instrumentation", id);
        }
    }

    protected static final CodeTree createPopUtility(TypeMirror type, BuilderVariables vars) {
        return CodeTreeBuilder.createBuilder().cast(type).startCall(vars.stackUtility, "pop").end().build();
    }

    protected static final CodeTree createPopUtility(CodeVariableElement target, BuilderVariables vars) {
        return CodeTreeBuilder.createBuilder().declaration(target.asType(), target.getName(), createPopUtility(target.asType(), vars)).build();
    }

    protected static final CodeTree createPushUtility(CodeVariableElement target, BuilderVariables vars) {
        return CodeTreeBuilder.createBuilder().startStatement().startCall(vars.stackUtility, "push").variable(target).end(2).build();
    }

    /**
     * 0 = TOS, 1 = TOS-1, ...
     *
     * @param target
     * @param vars
     * @param offset
     * @return
     */
    protected static final CodeTree createPeekUtility(CodeVariableElement target, BuilderVariables vars, int offset) {
        return CodeTreeBuilder.createBuilder().declaration(target.asType(), target.getName(), createPeekUtility(target.asType(), vars, offset)).build();
    }

    protected static final CodeTree createPeekUtility(TypeMirror type, BuilderVariables vars, int offset) {
        if (offset == 0) {
            return CodeTreeBuilder.createBuilder().cast(type).startCall(vars.stackUtility, "peek").end().build();
        } else {
            return CodeTreeBuilder.createBuilder().cast(type).startCall(vars.stackUtility, "get") //
                            .startGroup() //
                            .startCall(vars.stackUtility, "size").end() //
                            .string(" - " + (offset + 1)) //
                            .end(2).build();
        }
    }

    protected final CodeTree createPopLastChildCode(BuilderVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.startFor().string("int i = 0; i < ", vars.lastChildPushCount.getName(), "; i++").end();
        b.startBlock(); // {

        b.tree(createEmitInstruction(vars, builder.commonPop));

        b.end(); // }
        return b.build();
    }

}
