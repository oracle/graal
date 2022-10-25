package com.oracle.truffle.dsl.processor.operations.instructions;

import java.util.List;
import java.util.function.Function;

import javax.lang.model.type.ArrayType;

import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.operations.OperationsContext;

public class SuperInstruction extends Instruction {

    private final Instruction[] instructions;

    public Instruction[] getInstructions() {
        return instructions;
    }

    public SuperInstruction(OperationsContext ctx, int id, Instruction... instrs) {
        super(ctx, makeName(instrs), id, 0);
        this.instructions = instrs;
    }

    private static String makeName(Instruction[] instrs) {
        StringBuilder sb = new StringBuilder("si");
        for (Instruction i : instrs) {
            sb.append(".");
            sb.append(i.name);
        }

        return sb.toString();
    }

    private static void createExecute(CodeTreeBuilder b, ExecutionVariables vars, Instruction instr, Function<ExecutionVariables, CodeTree> exec) {
        // todo: merge this with OpByCoGe code, since now we have duplication (and probably bugs)
        if (instr.splitOnBoxingElimination()) {
            b.startSwitch().string("unsafeFromBytecode($bc, $bci) & 7").end().startBlock();

            for (FrameKind kind : instr.getBoxingEliminationSplits()) {
                b.startCase().string(kind.toOrdinal()).end().startBlock();
                vars.specializedKind = kind;
                b.tree(exec.apply(vars));
                vars.specializedKind = null;
                b.statement("break");
                b.end();
            }

            if (instr.hasGeneric()) {
                b.startCase().string("7 /* generic */").end().startBlock();
                b.tree(exec.apply(vars));
                b.statement("break");
                b.end();
            }

            b.end();
        } else if (instr.numPushedValues == 0 || instr.alwaysBoxed()) {
            b.tree(exec.apply(vars));
        } else {
            b.startBlock();
            b.declaration("int", "primitiveTag", "unsafeFromBytecode($bc, $bci) & 7");
            b.tree(exec.apply(vars));
            b.end();
        }
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        for (Instruction instr : instructions) {
            createExecute(b, vars, instr, instr::createExecuteCode);
            if (!instr.isBranchInstruction()) {
                b.startAssign(vars.bci).variable(vars.bci).string(" + ").tree(instr.createLength()).end();
            }
        }

        if (!instructions[instructions.length - 1].isBranchInstruction()) {
            b.statement("continue loop");
        }

        return b.build();
    }

    @Override
    public CodeTree createExecuteUncachedCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        for (Instruction instr : instructions) {
            b.startBlock();
            b.tree(instr.createExecuteUncachedCode(vars));
            if (!instr.isBranchInstruction()) {
                b.startAssign(vars.bci).variable(vars.bci).string(" + ").tree(instr.createLength()).end();
            }
            b.end();
        }
        if (!instructions[instructions.length - 1].isBranchInstruction()) {
            b.statement("continue loop");
        }

        return b.build();
    }

    @Override
    public boolean isBranchInstruction() {
        return true;
    }

    @Override
    protected int length() {
        int len = 0;
        for (Instruction i : instructions) {
            len += i.length();
        }

        return len;
    }

    @Override
    public CodeTree createDumpCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        b.statement("int oldBci = $bci");

        b.startAssign("Object[] decSuper");
        b.startNewArray((ArrayType) context.getType(Object[].class), null);
        b.string("$bci");
        b.doubleQuote(name);
        b.startGroup().string("Arrays.copyOfRange($bc, $bci, $bci + ").tree(createLength()).string(")").end();
        b.startNewArray((ArrayType) context.getType(Object[].class), null).end();
        b.startNewArray((ArrayType) context.getType(Object[].class), CodeTreeBuilder.singleString("" + instructions.length)).end();
        b.end(2); // outer array, assign

        for (int i = 0; i < instructions.length; i++) {
            b.startBlock();
            b.tree(instructions[i].createDumpCode(vars));
            b.startAssign("((Object[]) decSuper[4])[" + i + "]").string("dec").end();
            b.end();

            b.startStatement().string("$bci += ").tree(instructions[i].createLength()).end();
        }

        b.startAssign("Object[] dec").string("decSuper").end();

        b.statement("$bci = oldBci");

        return b.build();
    }

    @Override
    public CodeTree createPrepareAOT(ExecutionVariables vars, CodeTree language, CodeTree root) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean alwaysBoxed() {
        return instructions[0].alwaysBoxed();
    }

    @Override
    public List<FrameKind> getBoxingEliminationSplits() {
        return instructions[0].getBoxingEliminationSplits();
    }

    @Override
    public boolean splitOnBoxingElimination() {
        return false;
    }
}
