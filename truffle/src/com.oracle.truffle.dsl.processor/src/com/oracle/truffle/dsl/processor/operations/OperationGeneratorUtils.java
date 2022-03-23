package com.oracle.truffle.dsl.processor.operations;

import java.util.List;

import javax.lang.model.element.VariableElement;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeKind;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.operations.Instruction.ExecuteVariables;
import com.oracle.truffle.dsl.processor.operations.Operation.BuilderVariables;

public class OperationGeneratorUtils {

    public static TruffleTypes getTypes() {
        return ProcessorContext.getInstance().getTypes();
    }

    public static String toScreamCase(String s) {
        return s.replaceAll("([a-z])([A-Z])", "$1_$2").replace('.', '_').toUpperCase();
    }

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

    public static CodeTree createClearStackSlot(ExecuteVariables vars, String offset) {
        return CodeTreeBuilder.createBuilder() //
                        .startIf().tree(GeneratorUtils.createInCompiledCode()).end() //
                        .startBlock() //
                        .startStatement() //
                        .startCall(vars.frame, "clear") //
                        .startGroup().variable(vars.sp).string(" - 1 + (" + offset + ")")//
                        .end(4).build();
    }

    public static CodeTree createClearStackSlot(ExecuteVariables vars, int offset) {
        return createClearStackSlot(vars, "" + offset);
    }

    public static CodeTree createReadStack(ExecuteVariables vars, int offset) {
        return createReadStack(vars, CodeTreeBuilder.singleString("" + offset));
    }

    public static CodeTree createReadStack(ExecuteVariables vars, CodeTree offset) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        if (vars.tracer != null) {
            b.startCall(vars.tracer, "tracePop");
        }
        b.startCall(vars.frame, "getValue");
        b.startGroup().variable(vars.sp).string(" - 1 + (").tree(offset).string(")").end();
        if (vars.tracer != null) {
            b.end();
        }
        return b.end(2).build();
    }

    public static CodeTree createWriteStackObject(ExecuteVariables vars, int offset, CodeTree value) {
        return createWriteStackObject(vars, "" + offset, value);
    }

    public static CodeTree createWriteStackObject(ExecuteVariables vars, String offset, CodeTree value) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.startStatement().startCall(vars.frame, "setObject");
        b.startGroup().variable(vars.sp).string(" - 1 + (" + offset + ")").end();
        if (vars.tracer != null) {
            b.startCall(vars.tracer, "tracePush");
        }
        b.tree(value);
        if (vars.tracer != null) {
            b.end();
        }
        return b.end(2).build();
    }

    public static CodeTree createReadLocal(ExecuteVariables vars, CodeTree offset) {
        return CodeTreeBuilder.createBuilder() //
                        .startCall(vars.frame, "getValue") //
                        .startGroup().tree(offset).string(" + VALUES_OFFSET").end()//
                        .end(2).build();
    }

    public static CodeTree createWriteLocal(ExecuteVariables vars, CodeTree offset, CodeTree value) {
        return CodeTreeBuilder.createBuilder() //
                        .startStatement().startCall(vars.frame, "setObject") //
                        .startGroup().tree(offset).string(" + VALUES_OFFSET").end() //
                        .tree(value) //
                        .end(3).build();
    }

    public static CodeTree changeAllVariables(CodeTree tree, VariableElement from, VariableElement to) {
        // EXTREME HACK

        if (tree.getCodeKind() == CodeTreeKind.STRING && tree.getString().equals(from.getSimpleName().toString())) {
            return CodeTreeBuilder.singleString(to.getSimpleName().toString());
        } else {
            List<CodeTree> enc = tree.getEnclosedElements();
            if (enc == null) {
                return tree;
            }
            for (int i = 0; i < enc.size(); i++) {
                CodeTree res = changeAllVariables(enc.get(i), from, to);
                enc.set(i, res);
            }
            return tree;
        }
    }
}
