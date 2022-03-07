package com.oracle.truffle.dsl.processor.operations;

import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;

public class OperationGeneratorUtils {

    static void buildWriteByte(
                    CodeTreeBuilder b,
                    String value,
                    CodeVariableElement varBc,
                    CodeVariableElement varBci) {
        b.startStatement();
        b.variable(varBc).string("[").variable(varBci).string("++] = ", value);
        b.end();
    }

    static CodeTree buildReadByte(
                    CodeVariableElement varBc,
                    String from) {
        return CodeTreeBuilder.singleString(varBc.getName() + "[" + from + "]");
    }

    static CodeTree buildPop(
                    CodeVariableElement varStack,
                    CodeVariableElement varSp) {
        return CodeTreeBuilder.createBuilder().variable(varStack).string("[--").variable(varSp).string("]").build();
    }

    static CodeTree buildPush(
                    CodeVariableElement varStack,
                    CodeVariableElement varSp,
                    String value) {
        return CodeTreeBuilder.createBuilder().startStatement().variable(varStack).string("[").variable(varSp).string("++] = " + value).build();
    }

    static void buildWriteShort(
                    CodeTreeBuilder b,
                    String value,
                    CodeVariableElement varBc,
                    CodeVariableElement varBci) {
        b.startStatement();
        b.startCall("LE_BYTES", "putShort");
        b.variable(varBc).variable(varBci).string("(short)" + value);
        b.end(2); // call, statement

        b.startStatement();
        b.variable(varBci).string(" += 2");
        b.end();
    }

    static CodeTree buildReadShort(
                    CodeVariableElement varBc,
                    String from) {
        return CodeTreeBuilder.createBuilder().startCall("LE_BYTES", "getShort").variable(varBc).string(from).end().build();
    }

    static void buildWriteForwardReference(
                    CodeTreeBuilder b,
                    String varFwdRef,
                    CodeVariableElement varBc,
                    CodeVariableElement varBci) {
        b.startStatement();
        b.startCall("LE_BYTES", "putShort");
        b.variable(varBc).string(varFwdRef);
        b.startGroup().string("(short) ").variable(varBci).end();
        b.end(2); // call, statement
    }

    static void buildChildCall(
                    CodeTreeBuilder b,
                    String i,
                    Operation.EmitterVariables vars) {
        b.startAssign(vars.bci);
        b.startCall(vars.children.getSimpleName().toString() + "[" + i + "]", vars.self);
        b.variable(vars.bc).variable(vars.bci).variable(vars.consts);
        b.end(2);
    }
}
