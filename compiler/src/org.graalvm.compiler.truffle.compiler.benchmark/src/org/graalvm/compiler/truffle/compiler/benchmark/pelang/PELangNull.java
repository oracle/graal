package org.graalvm.compiler.truffle.compiler.benchmark.pelang;

public final class PELangNull {

    public static final PELangNull Instance = new PELangNull();

    private PELangNull() {
    }

    @Override
    public String toString() {
        return "null";
    }

}
