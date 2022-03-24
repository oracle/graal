package com.oracle.truffle.dsl.processor.operations;

import java.util.List;

import javax.lang.model.element.VariableElement;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeKind;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.operations.Operation.BuilderVariables;
import com.oracle.truffle.dsl.processor.operations.instructions.Instruction;

public class OperationGeneratorUtils {

    public static TruffleTypes getTypes() {
        return ProcessorContext.getInstance().getTypes();
    }

    public static String toScreamCase(String s) {
        return s.replaceAll("([a-z])([A-Z])", "$1_$2").replace('.', '_').toUpperCase();
    }

    public static CodeTree createEmitInstruction(BuilderVariables vars, Instruction instr, CodeVariableElement... arguments) {
        CodeTree[] trees = new CodeTree[arguments.length];
        for (int i = 0; i < trees.length; i++) {
            trees[i] = CodeTreeBuilder.singleVariable(arguments[i]);
        }

        return createEmitInstruction(vars, instr, trees);
    }

    public static CodeTree createEmitInstruction(BuilderVariables vars, Instruction instr, CodeTree... arguments) {
        return instr.createEmitCode(vars, arguments);
    }

    public static CodeTree createCreateLabel() {
        return CodeTreeBuilder.createBuilder().cast(getTypes().BuilderOperationLabel).startCall("createLabel").end().build();
    }

    public static CodeTree createEmitLabel(BuilderVariables vars, CodeTree label) {
        return CodeTreeBuilder.createBuilder().startStatement().startCall("doEmitLabel").variable(vars.bci).tree(label).end(2).build();
    }

    public static CodeTree createEmitLabel(BuilderVariables vars, CodeVariableElement label) {
        return createEmitLabel(vars, CodeTreeBuilder.singleVariable(label));
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
