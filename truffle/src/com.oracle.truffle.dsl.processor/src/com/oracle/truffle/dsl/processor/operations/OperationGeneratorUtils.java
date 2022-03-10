package com.oracle.truffle.dsl.processor.operations;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.operations.Instruction.ExecuteVariables;
import com.oracle.truffle.dsl.processor.operations.Operation.BuilderVariables;

public class OperationGeneratorUtils {

    public static TruffleTypes getTypes() {
        return ProcessorContext.getInstance().getTypes();
    }

    // new

    public static CodeTree createEmitInstruction(BuilderVariables vars, Instruction instr, CodeVariableElement... arguments) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.startAssign(vars.curStack).variable(vars.curStack).string(" + ").tree(instr.createStackEffect(vars, arguments)).end();
        b.startIf().variable(vars.maxStack).string(" < ").variable(vars.curStack).end();
        b.startAssign(vars.maxStack).variable(vars.curStack).end();
        // b.statement("System.out.printf(\" " + instr.name + " %d %d \\n\", maxStack, curStack)");
        b.tree(instr.createBuildCode(vars, arguments));
        return b.build();
    }

    public static CodeTree createCreateLabel() {
        return CodeTreeBuilder.createBuilder().startNew(getTypes().BuilderOperationLabel).end().build();
    }

    public static CodeTree createEmitLabel(BuilderVariables vars, CodeTree label) {
        return CodeTreeBuilder.createBuilder().startStatement().startCall(label, "resolve").variable(vars.bc).variable(vars.bci).end(2).build();
    }

    public static CodeTree createEmitLabel(BuilderVariables vars, CodeVariableElement label) {
        return createEmitLabel(vars, CodeTreeBuilder.singleVariable(label));
    }

    public static CodeTree createClearStackSlot(ExecuteVariables vars, int offset) {
        return CodeTreeBuilder.createBuilder() //
                        .startIf().tree(GeneratorUtils.createInCompiledCode()).end() //
                        .startBlock() //
                        .startStatement() //
                        .startCall(vars.frame, "clear") //
                        .startGroup().variable(vars.sp).string(" + " + (offset - 1))//
                        .end(4).build();
    }

    public static CodeTree createReadStack(ExecuteVariables vars, int offset) {
        return CodeTreeBuilder.createBuilder() //
                        .startCall(vars.frame, "getValue") //
                        .startGroup().variable(vars.sp).string(" + " + (offset - 1)).end()//
                        .end(2).build();
    }

    public static CodeTree createReadStack(ExecuteVariables vars, CodeTree offset) {
        return CodeTreeBuilder.createBuilder() //
                        .startCall(vars.frame, "getValue") //
                        .startGroup().variable(vars.sp).string(" - 1 + ").tree(offset).end()//
                        .end(2).build();
    }

    public static CodeTree createWriteStackObject(ExecuteVariables vars, int offset, CodeTree value) {
        return CodeTreeBuilder.createBuilder() //
                        .startStatement().startCall(vars.frame, "setObject") //
                        .startGroup().variable(vars.sp).string(" + " + (offset - 1)).end()//
                        .tree(value) //
                        .end(3).build();
    }

    public static CodeTree createReadLocal(ExecuteVariables vars, CodeTree offset) {
        return CodeTreeBuilder.createBuilder() //
                        .startCall(vars.frame, "getValue") //
                        .startGroup().variable(vars.maxStack).string(" + ").tree(offset).end()//
                        .end(2).build();
    }

    public static CodeTree createWriteLocal(ExecuteVariables vars, CodeTree offset, CodeTree value) {
        return CodeTreeBuilder.createBuilder() //
                        .startStatement().startCall(vars.frame, "setObject") //
                        .startGroup().variable(vars.maxStack).string(" + ").tree(offset).end() //
                        .tree(value) //
                        .end(3).build();
    }
}
