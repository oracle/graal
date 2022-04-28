package com.oracle.truffle.dsl.processor.operations.instructions;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.generator.TypeSystemCodeGenerator;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.model.TypeSystemData;
import com.oracle.truffle.dsl.processor.operations.OperationsContext;

public class ConditionalBranchInstruction extends Instruction {

    private final ProcessorContext context = ProcessorContext.getInstance();
    private final DeclaredType ConditionProfile = context.getDeclaredType("com.oracle.truffle.api.profiles.ConditionProfile");
    private OperationsContext ctx;

    public ConditionalBranchInstruction(OperationsContext ctx, int id) {
        super("branch.false", id, ResultType.BRANCH, InputType.BRANCH_TARGET, InputType.STACK_VALUE, InputType.BRANCH_PROFILE);
        this.ctx = ctx;
    }

    @Override
    public TypeMirror[] expectedInputTypes(ProcessorContext c) {
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

    @SuppressWarnings("unused")
    private CodeTree createBranchTarget(ExecutionVariables vars) {
        return CodeTreeBuilder.singleString("LE_BYTES.getShort(bc, bci + " + getArgumentOffset(0) + ")");
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        b.declaration(ConditionProfile, "profile", "conditionProfiles[LE_BYTES.getShort(bc, bci + " + getArgumentOffset(2) + ")]");

        // TODO: we should do (un)boxing elim here (but only if booleans are boxing elim'd)
        TypeSystemData data = ctx.getData().getTypeSystem();
        CodeTree conditionCode = TypeSystemCodeGenerator.cast(data, new CodeTypeMirror(TypeKind.BOOLEAN), CodeTreeBuilder.singleString("frame.getObject(sp - 1)"));

        b.declaration("boolean", "cond", conditionCode);

        b.statement("sp -= 1");

        b.startIf().startCall("profile", "profile").string("cond").end(2);
        b.startBlock();
        b.startAssign(vars.bci).variable(vars.bci).string(" + " + length()).end();
        b.statement("continue loop");
        b.end().startElseBlock();
        b.startAssign(vars.bci).tree(createBranchTarget(vars)).end();
        b.statement("continue loop");
        b.end();

        return b.build();
    }

    @Override
    public CodeTree createSetResultBoxed(ExecutionVariables vars, CodeVariableElement varBoxed, CodeVariableElement varTargetType) {
        return null;
    }

    @Override
    public CodeTree createPrepareAOT(ExecutionVariables vars, CodeTree language, CodeTree root) {
        return null;
    }

    @Override
    public CodeTree[] createTracingArguments(ExecutionVariables vars) {
        return new CodeTree[]{
                        CodeTreeBuilder.singleString("ExecutionTracer.INSTRUCTION_TYPE_BRANCH_COND"),
                        createBranchTarget(vars)
        };
    }
}