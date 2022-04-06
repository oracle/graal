package com.oracle.truffle.dsl.processor.operations.instructions;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;

public class ConditionalBranchInstruction extends Instruction {

    private final DeclaredType ConditionProfile = ProcessorContext.getInstance().getDeclaredType("com.oracle.truffle.api.profiles.ConditionProfile");

    public ConditionalBranchInstruction(int id) {
        super("branch.false", id, ResultType.BRANCH, InputType.BRANCH_TARGET, InputType.STACK_VALUE, InputType.BRANCH_PROFILE);
    }

    @Override
    public TypeMirror[] expectedInputTypes(ProcessorContext context) {
        return new TypeMirror[]{
                        context.getType(short.class),
                        context.getType(boolean.class),
                        ConditionProfile
        };
    }

    @Override
    public boolean standardPrologue() {
        return false;
    }

    @Override
    public boolean isBranchInstruction() {
        return true;
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        b.declaration(ConditionProfile, "profile", "conditionProfiles[LE_BYTES.getShort(bc, bci + 3)]");
        b.declaration("boolean", "cond", "frame.isBoolean(sp - 1) ? frame.getBoolean(sp - 1) : (boolean) frame.getObject(sp - 1)");
        b.statement("sp -= 1");

        b.startIf().startCall("profile", "profile").string("cond").end(2);
        b.startBlock();
        b.startAssign(vars.bci).variable(vars.bci).string(" + " + length()).end();
        b.statement("continue loop");
        b.end().startElseBlock();
        b.startAssign(vars.bci).string("LE_BYTES.getShort(bc, bci + 1)").end();
        b.statement("continue loop");
        b.end();

        return b.build();
    }
}