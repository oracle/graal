package org.graalvm.compiler.truffle.pelang;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;

@NodeChild(value = "argumentNode", type = PELangExpressionNode.class)
public abstract class PELangPrintNode extends PELangStatementNode {

    @Specialization
    public void println(long argument) {
        doPrint(argument);
    }

    @TruffleBoundary
    private static void doPrint(long argument) {
        System.out.println(argument);
    }

    @Specialization
    public void println(String argument) {
        doPrint(argument);
    }

    @TruffleBoundary
    private static void doPrint(String argument) {
        System.out.println(argument);
    }

    @Specialization
    public void println(Object argument) {
        doPrint(argument);
    }

    @TruffleBoundary
    private static void doPrint(Object argument) {
        System.out.println(argument);
    }

    public static PELangPrintNode createNode(PELangExpressionNode argumentNode) {
        return PELangPrintNodeGen.create(argumentNode);
    }

}
