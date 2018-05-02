package org.graalvm.compiler.truffle.pelang;

import com.oracle.truffle.api.nodes.ControlFlowException;

public final class PELangReturnException extends ControlFlowException {

    private static final long serialVersionUID = 1L;

    private final Object result;

    public PELangReturnException(Object result) {
        this.result = result;
    }

    public Object getResult() {
        return result;
    }

}
