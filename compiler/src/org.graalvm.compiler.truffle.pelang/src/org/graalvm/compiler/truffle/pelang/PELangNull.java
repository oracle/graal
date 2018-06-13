package org.graalvm.compiler.truffle.pelang;

public class PELangNull {

    private static final PELangNull INSTANCE = new PELangNull();

    private PELangNull() {
    }

    @Override
    public String toString() {
        return "PELangNull";
    }

    public static PELangNull getInstance() {
        return INSTANCE;
    }

}
