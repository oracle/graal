package com.oracle.truffle.dsl.processor.operations;

import static com.oracle.truffle.dsl.processor.operations.OperationGeneratorUtils.createCreateLabel;
import static com.oracle.truffle.dsl.processor.operations.OperationGeneratorUtils.createEmitInstruction;
import static com.oracle.truffle.dsl.processor.operations.OperationGeneratorUtils.createEmitLabel;
import static com.oracle.truffle.dsl.processor.operations.OperationGeneratorUtils.getTypes;

import java.util.List;

import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.operations.instructions.FrameKind;
import com.oracle.truffle.dsl.processor.operations.instructions.Instruction;
import com.oracle.truffle.dsl.processor.operations.instructions.Instruction.ExecutionVariables;
import com.oracle.truffle.dsl.processor.operations.instructions.Instruction.ResultType;
import com.oracle.truffle.dsl.processor.operations.instructions.LoadConstantInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.ShortCircuitInstruction;

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
        public CodeVariableElement bc;
        public CodeVariableElement bci;
        public CodeVariableElement consts;
        public CodeVariableElement operationData;
        public CodeVariableElement lastChildPushCount;
        public CodeVariableElement childIndex;
        public CodeVariableElement numChildren;

        public ExecutionVariables asExecution() {
            ExecutionVariables result = new ExecutionVariables();
            result.bc = this.bc;
            result.bci = this.bci;
            return result;
        }
    }

    public int minimumChildren() {
        assert isVariableChildren() : "should only be called for variadics";
        return 0;
    }

    public abstract List<TypeMirror> getBuilderArgumentTypes();

    public boolean needsOperationData() {
        return true;
    }

    @SuppressWarnings("unused")
    public CodeTree createBeginCode(BuilderVariables vars) {
        return null;
    }

    @SuppressWarnings("unused")
    public CodeTree createAfterChildCode(BuilderVariables vars) {
        return null;
    }

    @SuppressWarnings("unused")
    public CodeTree createBeforeChildCode(BuilderVariables vars) {
        return null;
    }

    @SuppressWarnings("unused")
    public CodeTree createEndCode(BuilderVariables vars) {
        return null;
    }

    @SuppressWarnings("unused")
    public CodeTree createLeaveCode(BuilderVariables vars) {
        return null;
    }

    public boolean hasLeaveCode() {
        return false;
    }

    public int getNumAuxValues() {
        return 0;
    }

    public int numLocalReferences() {
        return 0;
    }

    public boolean isRealOperation() {
        return true;
    }

    public abstract CodeTree createPushCountCode(BuilderVariables vars);

    public static class Simple extends Operation {

        private final Instruction instruction;

        protected Simple(OperationsContext builder, String name, int id, int children, Instruction instruction) {
            super(builder, name, id, children);
            this.instruction = instruction;
        }

        @Override
        public CodeTree createEndCode(BuilderVariables vars) {
            CodeTree[] arguments = new CodeTree[instruction.inputs.length + instruction.results.length];

            for (int i = 0; i < arguments.length; i++) {
                arguments[i] = CodeTreeBuilder.createBuilder().string("operationData.arguments[" + i + "]").build();
            }

            return OperationGeneratorUtils.createEmitInstruction(vars, instruction, arguments);

        }

        @Override
        public CodeTree createPushCountCode(BuilderVariables vars) {
            int result = 0;
            for (int i = 0; i < instruction.results.length; i++) {
                if (instruction.results[i] == ResultType.STACK_VALUE) {
                    result++;
                }
            }
            return CodeTreeBuilder.singleString("" + result);
        }

        @Override
        public List<TypeMirror> getBuilderArgumentTypes() {
            return instruction.getBuilderArgumentTypes();
        }

        @Override
        public int numLocalReferences() {
            return instruction.numLocalReferences();
        }
    }

    public static class LoadConstant extends Operation {
        private final LoadConstantInstruction[] instructions;

        protected LoadConstant(OperationsContext builder, int id, LoadConstantInstruction... instructions) {
            super(builder, "ConstObject", id, 0);
            this.instructions = instructions;
        }

        @Override
        public CodeTree createEndCode(BuilderVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            CodeTree[] arguments = new CodeTree[]{CodeTreeBuilder.singleString("arg0")};

            b.tree(OperationGeneratorUtils.createEmitInstruction(vars, instructions[FrameKind.OBJECT.ordinal()], arguments));

            return b.build();
        }

        @Override
        public List<TypeMirror> getBuilderArgumentTypes() {
            return List.of(ProcessorContext.getInstance().getType(Object.class));
        }

        @Override
        public CodeTree createPushCountCode(BuilderVariables vars) {
            return CodeTreeBuilder.singleString("1");
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
        public CodeTree createBeginCode(BuilderVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.startAssign(vars.lastChildPushCount).string("0").end();

            return b.build();
        }

        @Override
        public CodeTree createBeforeChildCode(BuilderVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.startIf().variable(vars.childIndex).string(" != 0").end();
            b.startBlock();
            {
                b.tree(createPopLastChildCode(vars));
            }
            b.end();

            return b.build();
        }

        @Override
        public CodeTree createPushCountCode(BuilderVariables vars) {
            return null; // does not change it at all
        }

        @Override
        public List<TypeMirror> getBuilderArgumentTypes() {
            return List.of();
        }
    }

    public static class IfThen extends Operation {
        protected IfThen(OperationsContext builder, int id) {
            super(builder, "IfThen", id, 2);
        }

        @Override
        public int getNumAuxValues() {
            return 1;
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

                b.tree(createSetAux(vars, 0, CodeTreeBuilder.singleVariable(varEndLabel)));

                b.tree(createEmitInstruction(vars, builder.commonBranchFalse, varEndLabel));
            }
            b.end().startElseBlock();
            {
                b.tree(createPopLastChildCode(vars));

                b.tree(createEmitLabel(vars, createGetAux(vars, 0, getTypes().BuilderOperationLabel)));
            }
            b.end();

            return b.build();
        }

        @Override
        public CodeTree createPushCountCode(BuilderVariables vars) {
            return CodeTreeBuilder.singleString("0");
        }

        @Override
        public List<TypeMirror> getBuilderArgumentTypes() {
            return List.of();
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
        public int getNumAuxValues() {
            return 2;
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

                b.tree(createSetAux(vars, 0, CodeTreeBuilder.singleVariable(varElseLabel)));

                b.tree(createEmitInstruction(vars, builder.commonBranchFalse, varElseLabel));
            }
            b.end();
            b.startElseIf().variable(vars.childIndex).string(" == 1").end();
            b.startBlock(); // {
            {
                CodeVariableElement varEndLabel = new CodeVariableElement(getTypes().BuilderOperationLabel, "endLabel");

                if (hasValue) {
                    b.startAssert().variable(vars.lastChildPushCount).string(" == 1").end();
                } else {
                    b.tree(createPopLastChildCode(vars));
                }

                b.declaration(getTypes().BuilderOperationLabel, varEndLabel.getName(), createCreateLabel());

                b.tree(createSetAux(vars, 1, CodeTreeBuilder.singleVariable(varEndLabel)));

                b.tree(createEmitInstruction(vars, builder.commonBranch, varEndLabel));
                b.tree(createEmitLabel(vars, createGetAux(vars, 0, getTypes().BuilderOperationLabel)));
            }
            b.end().startElseBlock();
            {

                if (hasValue) {
                    b.startAssert().variable(vars.lastChildPushCount).string(" == 1").end();
                } else {
                    b.tree(createPopLastChildCode(vars));
                }

                b.tree(createEmitLabel(vars, createGetAux(vars, 1, getTypes().BuilderOperationLabel)));
            }
            b.end();

            return b.build();
        }

        @Override
        public List<TypeMirror> getBuilderArgumentTypes() {
            return List.of();
        }
    }

    public static class While extends Operation {
        public While(OperationsContext builder, int id) {
            super(builder, "While", id, 2);
        }

        private static final int AUX_START_LABEL = 0;
        private static final int AUX_END_LABEL = 1;

        @Override
        public int getNumAuxValues() {
            return 2;
        }

        @Override
        public CodeTree createBeginCode(BuilderVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            CodeVariableElement varStartLabel = new CodeVariableElement(getTypes().BuilderOperationLabel, "startLabel");
            b.declaration(getTypes().BuilderOperationLabel, varStartLabel.getName(), createCreateLabel());

            b.tree(createEmitLabel(vars, varStartLabel));

            b.tree(createSetAux(vars, AUX_START_LABEL, CodeTreeBuilder.singleVariable(varStartLabel)));

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

                b.tree(createSetAux(vars, AUX_END_LABEL, CodeTreeBuilder.singleVariable(varEndLabel)));

                b.tree(createEmitInstruction(vars, builder.commonBranchFalse, CodeTreeBuilder.singleVariable(varEndLabel)));
            }
            b.end().startElseBlock();
            {
                b.tree(createPopLastChildCode(vars));

                b.tree(createEmitInstruction(vars, builder.commonBranch, createGetAux(vars, AUX_START_LABEL, getTypes().BuilderOperationLabel)));

                b.tree(createEmitLabel(vars, createGetAux(vars, AUX_END_LABEL, getTypes().BuilderOperationLabel)));
            }

            b.end();

            return b.build();
        }

        @Override
        public CodeTree createPushCountCode(BuilderVariables vars) {
            return CodeTreeBuilder.singleString("0");
        }

        @Override
        public List<TypeMirror> getBuilderArgumentTypes() {
            return List.of();
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
            return createEmitLabel(vars, CodeTreeBuilder.singleString("arg0"));
        }

        @Override
        public List<TypeMirror> getBuilderArgumentTypes() {
            return List.of(ProcessorContext.getInstance().getTypes().OperationLabel);
        }

        @Override
        public boolean needsOperationData() {
            return false;
        }
    }

    public static class TryCatch extends Operation {
        public TryCatch(OperationsContext builder, int id) {
            super(builder, "TryCatch", id, 2);
        }

        private static final int AUX_BEH = 0;
        private static final int AUX_END_LABEL = 1;

        @Override
        public int getNumAuxValues() {
            return 2;
        }

        @Override
        public CodeTree createPushCountCode(BuilderVariables vars) {
            return CodeTreeBuilder.singleString("0");
        }

        @Override
        public CodeTree createBeginCode(BuilderVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            CodeVariableElement varBeh = new CodeVariableElement(getTypes().BuilderExceptionHandler, "beh");
            b.declaration(getTypes().BuilderExceptionHandler, "beh", CodeTreeBuilder.createBuilder().startNew(getTypes().BuilderExceptionHandler).end().build());
            b.startStatement().variable(varBeh).string(".startBci = ").variable(vars.bci).end();
            b.startStatement().variable(varBeh).string(".startStack = getCurStack()").end();
            b.startStatement().variable(varBeh).string(".exceptionIndex = (int)").variable(vars.operationData).string(".arguments[0]").end();
            b.startStatement().startCall("addExceptionHandler").variable(varBeh).end(2);

            b.tree(createSetAux(vars, AUX_BEH, CodeTreeBuilder.singleVariable(varBeh)));

            CodeVariableElement varEndLabel = new CodeVariableElement(getTypes().BuilderOperationLabel, "endLabel");
            b.declaration(getTypes().BuilderOperationLabel, varEndLabel.getName(), createCreateLabel());

            b.tree(createSetAux(vars, AUX_END_LABEL, CodeTreeBuilder.singleVariable(varEndLabel)));

            return b.build();
        }

        @Override
        public CodeTree createAfterChildCode(BuilderVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.tree(createPopLastChildCode(vars));

            b.startIf().variable(vars.childIndex).string(" == 0").end();
            b.startBlock();

            b.startStatement().tree(createGetAux(vars, AUX_BEH, getTypes().BuilderExceptionHandler)).string(".endBci = ").variable(vars.bci).end();

            b.tree(createEmitInstruction(vars, builder.commonBranch, createGetAux(vars, AUX_END_LABEL, getTypes().BuilderOperationLabel)));

            b.end().startElseBlock();

            b.end();

            return b.build();
        }

        @Override
        public CodeTree createBeforeChildCode(BuilderVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.startIf().variable(vars.childIndex).string(" == 1").end();
            b.startBlock();

            b.startStatement().startCall("setCurStack").startGroup().tree(createGetAux(vars, AUX_BEH, getTypes().BuilderExceptionHandler)).string(".startStack").end(3);
            b.startStatement().tree(createGetAux(vars, AUX_BEH, getTypes().BuilderExceptionHandler)).string(".handlerBci = ").variable(vars.bci).end();

            b.end();

            return b.build();
        }

        @Override
        public CodeTree createEndCode(BuilderVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.tree(createEmitLabel(vars, createGetAux(vars, AUX_END_LABEL, getTypes().BuilderOperationLabel)));

            return b.build();
        }

        @Override
        public List<TypeMirror> getBuilderArgumentTypes() {
            return List.of(new CodeTypeMirror(TypeKind.INT));
        }
    }

    public static class InstrumentTag extends Block {
        private final Instruction startInstruction;
        private final Instruction endInstruction;
        private final Instruction endVoidInstruction;
        private final Instruction leaveInstruction;

        public static final String NAME = "Tag";

        public InstrumentTag(OperationsContext builder, int id, Instruction startInstruction, Instruction endInstruction, Instruction endVoidInstruction, Instruction leaveInstruction) {
            super(builder, NAME, id);
            this.startInstruction = startInstruction;
            this.endInstruction = endInstruction;
            this.endVoidInstruction = endVoidInstruction;
            this.leaveInstruction = leaveInstruction;
        }

        private static final int AUX_ID = 0;
        private static final int AUX_START_LABEL = 1;
        private static final int AUX_END_LABEL = 2;

        @Override
        public int getNumAuxValues() {
            return 3;
        }

        @Override
        public List<TypeMirror> getBuilderArgumentTypes() {
            ProcessorContext context = ProcessorContext.getInstance();
            return List.of(context.getType(Class.class));
        }

        @Override
        public CodeTree createBeginCode(BuilderVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            CodeVariableElement varCurInstrumentId = new CodeVariableElement(new CodeTypeMirror(TypeKind.INT), "curInstrumentId");
            CodeVariableElement varStartLabel = new CodeVariableElement(getTypes().BuilderOperationLabel, "startLabel");
            CodeVariableElement varEndLabel = new CodeVariableElement(getTypes().BuilderOperationLabel, "endLabel");

            b.declaration("int", varCurInstrumentId.getName(), "doBeginInstrumentation((Class) arg0)");
            b.declaration(getTypes().BuilderOperationLabel, varStartLabel.getName(), createCreateLabel());
            b.declaration(getTypes().BuilderOperationLabel, varEndLabel.getName(), createCreateLabel());

            b.tree(createEmitLabel(vars, varStartLabel));

            b.tree(createSetAux(vars, AUX_ID, CodeTreeBuilder.singleVariable(varCurInstrumentId)));
            b.tree(createSetAux(vars, AUX_START_LABEL, CodeTreeBuilder.singleVariable(varStartLabel)));
            b.tree(createSetAux(vars, AUX_END_LABEL, CodeTreeBuilder.singleVariable(varEndLabel)));

            b.tree(createEmitInstruction(vars, startInstruction, varCurInstrumentId));

            b.tree(super.createBeginCode(vars));
            return b.build();
        }

        @Override
        public CodeTree createEndCode(BuilderVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.startIf().variable(vars.lastChildPushCount).string(" != 0").end();
            b.startBlock();
            b.tree(createEmitInstruction(vars, endInstruction, createGetAux(vars, AUX_ID, new CodeTypeMirror(TypeKind.INT))));
            b.end().startElseBlock();
            b.tree(createEmitInstruction(vars, endVoidInstruction, createGetAux(vars, AUX_ID, new CodeTypeMirror(TypeKind.INT))));
            b.end();

            b.tree(super.createEndCode(vars));
            return b.build();
        }

        @Override
        public CodeTree createLeaveCode(BuilderVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.tree(createEmitInstruction(vars, leaveInstruction,
                            createGetAux(vars, AUX_ID, new CodeTypeMirror(TypeKind.INT)),
                            createGetAux(vars, AUX_START_LABEL, getTypes().BuilderOperationLabel),
                            createGetAux(vars, AUX_END_LABEL, getTypes().BuilderOperationLabel)));

            return b.build();
        }

        @Override
        public boolean hasLeaveCode() {
            return true;
        }
    }

    public static class FinallyTry extends Operation {

        private final int AUX_CONTEXT = 0;
        private final int AUX_BEH = 1;
        private final int AUX_LOCAL = 2;

        private final boolean noExcept;

        public FinallyTry(OperationsContext builder, int id, boolean noExcept) {
            super(builder, noExcept ? "FinallyTryNoExcept" : "FinallyTry", id, 2);
            this.noExcept = noExcept;
        }

        @Override
        public List<TypeMirror> getBuilderArgumentTypes() {
            return List.of();
        }

        @Override
        public CodeTree createPushCountCode(BuilderVariables vars) {
            // TODO - this could be made to return Try value on exit
            return CodeTreeBuilder.singleString("0");
        }

        @Override
        public CodeTree createBeginCode(BuilderVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            if (!noExcept) {
                b.startStatement().variable(vars.operationData).string(".aux[" + AUX_LOCAL + "] = ");
                b.startCall("createParentLocal");
                b.end(2);
            }

            b.startStatement().variable(vars.operationData).string(".aux[" + AUX_CONTEXT + "] = ").startCall("doBeginFinallyTry").end(2);

            return b.build();
        }

        @Override
        public CodeTree createAfterChildCode(BuilderVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.tree(createPopLastChildCode(vars));

            b.startIf().variable(vars.childIndex).string(" == 0").end().startBlock();
            {
                b.startStatement().startCall("doEndFinallyBlock").end(2);

                if (!noExcept) {
                    CodeVariableElement varBeh = new CodeVariableElement(getTypes().BuilderExceptionHandler, "beh");
                    b.declaration(getTypes().BuilderExceptionHandler, "beh", CodeTreeBuilder.createBuilder().startNew(getTypes().BuilderExceptionHandler).end().build());

                    b.startStatement().variable(varBeh).string(".startBci = ").variable(vars.bci).end();
                    b.startStatement().variable(varBeh).string(".startStack = getCurStack()").end();
                    b.startStatement().variable(varBeh).string(".exceptionIndex = ");
                    b.startCall("getLocalIndex");
                    b.startGroup().variable(vars.operationData).string(".aux[" + AUX_LOCAL + "]").end();
                    b.end();
                    b.end();
                    b.startStatement().startCall("addExceptionHandler").variable(varBeh).end(2);
                    b.tree(createSetAux(vars, AUX_BEH, CodeTreeBuilder.singleVariable(varBeh)));
                }

            }
            b.end();
            return b.build();
        }

        @Override
        public CodeTree createLeaveCode(BuilderVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            emitLeaveCode(vars, b);

            return b.build();
        }

        @Override
        public CodeTree createEndCode(BuilderVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            if (noExcept) {
                emitLeaveCode(vars, b);
            } else {
                // ; exception end
                // << handler code >>
                // goto end
                // ; exception handler start
                // << handler code >>
                // throw [exc]
                // end:

                b.startAssign("int endBci").variable(vars.bci).end();

                emitLeaveCode(vars, b);

                CodeVariableElement varEndLabel = new CodeVariableElement(getTypes().BuilderOperationLabel, "endLabel");
                b.declaration(getTypes().BuilderOperationLabel, varEndLabel.getName(), createCreateLabel());

                b.startBlock();
                b.tree(OperationGeneratorUtils.createEmitInstruction(vars, builder.commonBranch, varEndLabel));
                b.end();

                b.declaration(getTypes().BuilderExceptionHandler, "beh", createGetAux(vars, AUX_BEH, getTypes().BuilderExceptionHandler));
                b.startAssign("beh.endBci").string("endBci").end();
                b.startAssign("beh.handlerBci").variable(vars.bci).end();

                emitLeaveCode(vars, b);

                b.startBlock();
                CodeTree localIdx = CodeTreeBuilder.createBuilder().variable(vars.operationData).string(".aux[" + AUX_LOCAL + "]").build();
                b.tree(OperationGeneratorUtils.createEmitInstruction(vars, builder.commonThrow, localIdx));
                b.end();

                b.tree(OperationGeneratorUtils.createEmitLabel(vars, varEndLabel));
            }

            return b.build();
        }

        private static void emitLeaveCode(BuilderVariables vars, CodeTreeBuilder b) {
            b.startStatement().startCall("doLeaveFinallyTry").variable(vars.operationData).end(2);
        }

        @Override
        public int getNumAuxValues() {
            return noExcept ? 1 : 3;
        }

        @Override
        public boolean hasLeaveCode() {
            return true;
        }
    }

    public static class ShortCircuitOperation extends Operation {

        private final ShortCircuitInstruction instruction;

        protected ShortCircuitOperation(OperationsContext builder, String name, int id, ShortCircuitInstruction instruction) {
            super(builder, name, id, VARIABLE_CHILDREN);
            this.instruction = instruction;
        }

        @Override
        public List<TypeMirror> getBuilderArgumentTypes() {
            return List.of();
        }

        @Override
        public int minimumChildren() {
            return 1;
        }

        @Override
        public int getNumAuxValues() {
            return 1; // only the end label
        }

        @Override
        public CodeTree createPushCountCode(BuilderVariables vars) {
            return CodeTreeBuilder.singleString("1");
        }

        @Override
        public CodeTree createBeginCode(BuilderVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.tree(createSetAux(vars, 0, createCreateLabel()));

            return b.build();
        }

        @Override
        public CodeTree createBeforeChildCode(BuilderVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.startIf().variable(vars.childIndex).string(" > 0").end().startBlock();
            {
                b.tree(OperationGeneratorUtils.createEmitInstruction(vars, instruction, createGetAux(vars, 0, getTypes().BuilderOperationLabel)));
            }
            b.end();

            return b.build();
        }

        @Override
        public CodeTree createEndCode(BuilderVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.tree(OperationGeneratorUtils.createEmitLabel(vars, createGetAux(vars, 0, getTypes().BuilderOperationLabel)));

            return b.build();
        }

    }

    public static class LocalSetter extends Operation {

        protected LocalSetter(OperationsContext context, int id) {
            super(context, "LocalSetter", id, 0);
        }

        @Override
        public List<TypeMirror> getBuilderArgumentTypes() {
            return List.of(getTypes().OperationLocal);
        }

        @Override
        public CodeTree createPushCountCode(BuilderVariables vars) {
            return CodeTreeBuilder.singleString("0");
        }

        @Override
        public CodeTree createBeginCode(BuilderVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.startStatement().variable(vars.operationData).string(".localReferences[").variable(vars.operationData).string(".numLocalReferences++] = arg0").end();

            return b.build();
        }

        @Override
        public boolean needsOperationData() {
            return false;
        }

        @Override
        public boolean isRealOperation() {
            return false;
        }
    }

    public static class LocalSetterArray extends Operation {

        protected LocalSetterArray(OperationsContext context, int id) {
            super(context, "LocalSetterArray", id, 0);
        }

        @Override
        public List<TypeMirror> getBuilderArgumentTypes() {
            return List.of(new CodeTypeMirror.ArrayCodeTypeMirror(getTypes().OperationLocal));
        }

        @Override
        public CodeTree createPushCountCode(BuilderVariables vars) {
            return CodeTreeBuilder.singleString("0");
        }

        @Override
        public CodeTree createBeginCode(BuilderVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.startStatement().variable(vars.operationData).string(".localReferences = arg0").end();

            return b.build();
        }

        @Override
        public boolean needsOperationData() {
            return false;
        }

        @Override
        public boolean isRealOperation() {
            return false;
        }
    }

    private static final CodeTree createSetAux(BuilderVariables vars, int index, CodeTree value) {
        return CodeTreeBuilder.createBuilder().startStatement()//
                        .variable(vars.operationData).string(".aux[" + index + "] = ") //
                        .tree(value) //
                        .end().build();
    }

    private static final CodeTree createGetAux(BuilderVariables vars, int index, TypeMirror cast) {
        return CodeTreeBuilder.createBuilder().string("(").cast(cast).variable(vars.operationData).string(".aux[" + index + "]").string(")").build();
    }

    protected final CodeTree createPopLastChildCode(BuilderVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.startFor().string("int i = 0; i < ", vars.lastChildPushCount.getName(), "; i++").end();
        b.startBlock(); // {

        b.tree(createEmitInstruction(vars, builder.commonPop, new CodeTree[0]));

        b.end(); // }
        return b.build();
    }

}
