package com.oracle.truffle.dsl.processor.operations;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import javax.lang.model.element.Element;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.java.transform.AbstractCodeWriter;
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

    public static CodeTree createReadOpcode(CodeTree bc, CodeTree bci) {
        return CodeTreeBuilder.createBuilder().tree(bc).string("[").tree(bci).string("]").build();
    }

    public static CodeTree createReadOpcode(CodeVariableElement bc, CodeVariableElement bci) {
        return createReadOpcode(
                        CodeTreeBuilder.singleVariable(bc),
                        CodeTreeBuilder.singleVariable(bci));
    }

    public static CodeTree createWriteOpcode(CodeTree bc, CodeTree bci, CodeTree value) {
        return CodeTreeBuilder.createBuilder().startStatement().tree(bc).string("[").tree(bci).string("] = ").tree(value).end().build();
    }

    public static CodeTree createWriteOpcode(CodeVariableElement bc, CodeVariableElement bci, CodeVariableElement value) {
        return createWriteOpcode(
                        CodeTreeBuilder.singleVariable(bc),
                        CodeTreeBuilder.singleVariable(bci),
                        CodeTreeBuilder.singleVariable(value));
    }

    public static String printCode(Element el) {
        StringWriter wr = new StringWriter();
        new AbstractCodeWriter() {
            {
                writer = wr;
            }

            @Override
            protected Writer createWriter(CodeTypeElement clazz) throws IOException {
                // TODO Auto-generated method stub
                return wr;
            }
        }.visit(el);

        return wr.toString();
    }

}
