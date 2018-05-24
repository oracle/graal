package org.graalvm.compiler.truffle.pelang;

import org.graalvm.compiler.truffle.pelang.util.PELangBuilder.FunctionHeader;

import com.oracle.truffle.api.RootCallTarget;

public final class PELangFunction {

    private final FunctionHeader header;
    private final RootCallTarget callTarget;

    public PELangFunction(FunctionHeader header, RootCallTarget callTarget) {
        this.header = header;
        this.callTarget = callTarget;
    }

    public FunctionHeader getHeader() {
        return header;
    }

    public RootCallTarget getCallTarget() {
        return callTarget;
    }

}
