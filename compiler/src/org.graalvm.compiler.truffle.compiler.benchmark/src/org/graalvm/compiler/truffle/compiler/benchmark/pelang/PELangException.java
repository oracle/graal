package org.graalvm.compiler.truffle.compiler.benchmark.pelang;

import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.nodes.Node;

public class PELangException extends RuntimeException implements TruffleException {

    private static final long serialVersionUID = 1L;

    private final Node node;

    public PELangException(String message, Node node) {
        super(message);
        this.node = node;
    }

    @Override
    public Node getLocation() {
        return node;
    }

}
